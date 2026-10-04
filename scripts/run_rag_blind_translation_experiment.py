"""Authorized blind translation of exactly 60 existing Chinese test queries.

Only query id/text are sent to the configured DeepSeek text model. Corpus,
answers, source references and gold anchors never enter translation prompts.
Platform configurations and production indexes remain unchanged.
"""
import json
import re
import sys
import time
from datetime import datetime, timezone

import numpy as np
import requests

from run_rag_model_chunk_experiment import ROOT, STAGE, Provider, build, digest, load_json, norm, write_json
from run_rag_m3_hybrid_translation_experiment import OUT, Redis, create_index, rank
from summarize_rag_m3_hybrid_experiment import score

DEST = OUT / 'blind-translation'


def config():
    env = {}
    for line in (ROOT/'.env.local').read_text(encoding='utf-8-sig').splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            k,v = line.split('=',1); env[k.strip()] = v.strip().strip('"').strip("'")
    endpoint = (env.get('AI_TEXT_ENDPOINT') or env.get('AI_TREND_ENDPOINT') or '').rstrip('/')
    if endpoint not in ['https://api.deepseek.com', 'https://api.deepseek.com/v1']:
        raise ValueError('Endpoint differs from authorized platform DeepSeek endpoint')
    model = env.get('AI_TEXT_MODEL') or env.get('AI_TREND_MODEL')
    key = env.get('AI_TEXT_KEY') or env.get('AI_TREND_KEY')
    if not model or not key: raise ValueError('Missing text configuration')
    return endpoint,model,key


def translate(cases):
    endpoint,model,key = config()
    cache = DEST/'translations.json'
    payload_hash = digest([{'id':c['key'],'text':c['question']} for c in cases])
    if cache.exists():
        data = load_json(cache)
        if data['inputHash'] != payload_hash or data['model'] != model: raise ValueError('Translation cache mismatch')
        return data['translations']
    partial = DEST/'translation-progress.json'
    completed = load_json(partial) if partial.exists() else {}
    if completed and completed.get('inputHash') != payload_hash: raise ValueError('Progress mismatch')
    translations = completed.get('translations',{})
    for start in range(0,len(cases),10):
        batch = [c for c in cases[start:start+10] if c['key'] not in translations]
        if not batch: continue
        messages = [
            {'role':'system','content':'You translate Chinese search queries into English. Translate faithfully, '
             'preserve all constraints and technical terms. Do not answer or infer project names or add facts. '
             'Return only a JSON array with exactly one {"id": "...", "text": "..."} object per input.'},
            {'role':'user','content':json.dumps([{'id':c['key'],'text':c['question']} for c in batch],ensure_ascii=False)}]
        print('BLIND_TRANSLATE '+str(start+1)+'-'+str(start+len(batch))+' model='+model,flush=True)
        started = time.perf_counter()
        response = requests.post(endpoint+'/chat/completions',headers={'Authorization':'Bearer '+key},
            json={'model':model,'messages':messages,'temperature':0,'max_tokens':2048,
                  'stream':False,'thinking':{'type':'disabled'}},timeout=120)
        event = {'at':datetime.now(timezone.utc).isoformat(),'endpoint':endpoint,'model':model,
            'purpose':'authorized-blind-query-translation','queries':len(batch),'status':response.status_code,
            'durationMs':round((time.perf_counter()-started)*1000,2),
            'inputCharacters':sum(len(m['content']) for m in messages),'requestHash':digest(messages)}
        if response.status_code != 200:
            with (DEST/'text-calls.jsonl').open('a',encoding='utf-8') as f:f.write(json.dumps(event)+'\n')
            raise RuntimeError('Translation HTTP_'+str(response.status_code))
        body = response.json(); event['usage'] = body.get('usage'); event['servedModel'] = body.get('model')
        content = body['choices'][0]['message']['content']
        with (DEST/'text-calls.jsonl').open('a',encoding='utf-8') as f:f.write(json.dumps(event)+'\n')
        content = re.sub(r'^```(?:json)?\s*|\s*```$', '', content.strip())
        rows = json.loads(content)
        if len(rows)!=len(batch) or {r['id'] for r in rows}!={c['key'] for c in batch}:
            raise ValueError('Invalid translation IDs')
        for r in rows:
            if not isinstance(r['text'],str) or not r['text'].strip(): raise ValueError('Empty translation')
            translations[r['id']] = r['text']
        write_json(partial,{'inputHash':payload_hash,'model':model,'translations':translations})
    write_json(cache,{'inputHash':payload_hash,'model':model,'translations':translations,
                     'promptInputs':'query ids and texts only; no gold evidence or corpus'})
    return translations


def main():
    sys.stdout.reconfigure(encoding='utf-8'); DEST.mkdir(parents=True,exist_ok=True)
    all_cases = load_json(OUT/'dataset.json')['cases']
    cases = [c for c in all_cases if c['category'] in
        ['business_zh','readme_cross_language','fresh_business_zh','fresh_readme_zh']]
    assert len(cases)==60
    translated = translate(cases)
    corpus = load_json(STAGE/'corpus.json')
    _,model,chunks,_ = next(p for p in build(corpus) if p[0]=='bge-m3--native512')
    provider = Provider(); redis = Redis()
    vectors = provider.embed(model,[c['content'] for c in chunks],'documents')
    # Preserve original query batch boundaries to reuse previously paid requests.
    original = provider.embed(model,[c['question'] for c in all_cases[:110]],'queries')
    fresh = provider.embed(model,[c['question'] for c in all_cases[110:]],'fresh-queries')
    q_lookup = {c['key']:q for c,q in zip(all_cases,np.concatenate([original,fresh]))}
    tq = provider.embed(model,[translated[c['key']] for c in cases],'blind-translated-queries')
    index,prefix = create_index(redis,chunks,vectors,'bge-m3--native512')
    results = {}
    for mode,hybrid,diverse,dual,threshold in [
        ('raw_vector',False,False,False,0),('blind_dual_vector',False,False,True,0),
        ('blind_dual_vector_raw080',False,False,True,0),
        ('blind_dual_vector_raw090',False,False,True,0),
        ('blind_english_only',False,False,False,0),
        ('raw_existing_hybrid_t055',True,True,False,.55),
        ('blind_dual_existing_hybrid_t055',True,True,True,.55)]:
        rows=[]
        for k,case in enumerate(cases):
            started=time.perf_counter()
            query_vector=tq[k] if mode=='blind_english_only' else q_lookup[case['key']]
            ids,trace=rank(redis,index,prefix,chunks,case,query_vector,tq[k] if dual else None,
                           hybrid,diverse,threshold,ef=64,
                           raw_weight=.8 if mode.endswith('raw080') else .9 if mode.endswith('raw090') else .5)
            hits=[]
            for r,i in enumerate(ids,1):
                ch=chunks[i]; gold=ch['sourceRef'] in case['refs'] and any(
                    norm(a) in norm(ch['content']) for a in case['anchors'])
                hits.append({'rank':r,'chunk':i,'sourceRef':ch['sourceRef'],'section':ch.get('section'),
                             'gold':gold,'start':ch['start'],'end':ch['end']})
            rows.append({'key':case['key'],'category':case['category'],
                'rank':next((h['rank'] for h in hits if h['gold']),0),'hits':hits,'trace':trace,
                'localRetrievalMs':(time.perf_counter()-started)*1000})
        result={'metrics':score(rows),'freshChinese30':score([r for r in rows if r['key'].startswith('fresh-')]),
            'categories':{cat:score([r for r in rows if r['category']==cat]) for cat in sorted({r['category'] for r in rows})},
            'localRetrievalP95Ms':float(np.percentile([r['localRetrievalMs'] for r in rows],95)), 'rows':rows}
        write_json(DEST/(mode+'.json'),result); results[mode]=result
        print('RESULT '+mode+' '+json.dumps(result['metrics']),flush=True)
    raw=results['raw_vector']['rows']; dual=results['blind_dual_vector']['rows']
    changes=[];group_delta=[]
    for i in range(30):
        keys={f'fact-{i:02}-zh',f'fresh-{i:02}'}
        vals=[]
        for a,b in zip(raw,dual):
            if a['key'] in keys:
                vals.append((1/b['rank'] if b['rank'] else 0)-(1/a['rank'] if a['rank'] else 0))
                if a['rank']!=b['rank']:changes.append({'key':a['key'],'rawRank':a['rank'],'dualRank':b['rank']})
        group_delta.append(np.mean(vals))
    rng=np.random.default_rng(20261002)
    sampled=np.array(group_delta)[rng.integers(0,30,size=(10000,30))].mean(axis=1)
    text_calls=[json.loads(l) for l in (DEST/'text-calls.jsonl').read_text(encoding='utf-8').splitlines()]
    embed_calls=[json.loads(l) for l in (STAGE/'calls.jsonl').read_text(encoding='utf-8').splitlines()
                 if json.loads(l)['purpose']=='blind-translated-queries']
    # Official pricing verified 2026-10-02. Estimate, not account debit verification.
    fees=[]
    for call in text_calls:
        at=datetime.fromisoformat(call['at']); usage=call.get('usage') or {}
        peak=at.weekday()<5 and (1<=at.hour<4 or 6<=at.hour<10)
        hit,miss,out=(.044,1.32,3.96) if peak else (.022,.66,1.98)
        assert usage.get('prompt_cache_hit_tokens',0)+usage.get('prompt_cache_miss_tokens',0)==usage.get('prompt_tokens',0)
        usd=(usage.get('prompt_cache_hit_tokens',0)*hit+
             usage.get('prompt_cache_miss_tokens',0)*miss+usage.get('completion_tokens',0)*out)/1_000_000
        fees.append({'at':call['at'],'peak':peak,'estimatedUSD':usd})
    report={'at':datetime.now(timezone.utc).isoformat(),'cases':60,'corpusHash':digest(corpus),
        'results':{name:{k:v for k,v in r.items() if k!='rows'} for name,r in results.items()},
        'rankChanges':changes,'dualMinusRawMRR':float(np.mean(group_delta)),
        'dualMinusRawMRR95CI_factGrouped':np.percentile(sampled,[2.5,97.5]).tolist(),
        'textCalls':text_calls,'newEmbeddingCalls':embed_calls,
        'translationCostEstimate':{'currency':'USD','total':sum(f['estimatedUSD'] for f in fees),
            'perCall':fees,'accountDebitVerified':False,'priceVerifiedOn':'2026-10-02',
            'source':'https://api-docs.deepseek.com/quick_start/pricing/',
            'peakPricePerMillion':{'cacheHit':.044,'cacheMiss':1.32,'output':3.96},
            'offPeakPricePerMillion':{'cacheHit':.022,'cacheMiss':.66,'output':1.98}},
        'translationBatchDurationMs':{'total':sum(e['durationMs'] for e in text_calls),
            'p50':float(np.percentile([e['durationMs'] for e in text_calls],50)),
            'p95':float(np.percentile([e['durationMs'] for e in text_calls],95))},
        'textTotals':{k:sum((e.get('usage') or {}).get(k,0) for e in text_calls) for k in
            ['prompt_tokens','completion_tokens','total_tokens','prompt_cache_hit_tokens','prompt_cache_miss_tokens']},
        'limitations':['Blind translator, but corpus gold is assistant-authored and not independent human evaluation.',
            'Fresh wording shares original facts; uncertainty grouped by 30 facts.',
            'Translation performed in batches of ten; batch latency is not individual online query latency.',
            'Embeddings are batch calls and retrieval timings exclude external translation and embedding latency.',
            'Weighted 80/20 and 90/10 fusion checks were exploratory, performed after observing 50/50 results.',
            'No generation, Java citation validation, business filters/preferences or online configuration changes.',
            'Translated-only hybrid lexical channel remains the original Chinese query: only vectors change.']}
    write_json(DEST/'report.json',report)
    decision_path=OUT/'decision-report.json'
    if decision_path.exists():
        decision=load_json(decision_path)
        decision['blindTranslationFollowup']={'report':'blind-translation/report.json',
            'authorization':'User explicitly allowed sending 60 Chinese test queries to configured DeepSeek.',
            'raw':results['raw_vector']['metrics'],'blindDual':results['blind_dual_vector']['metrics'],
            'textCalls':len(text_calls),'estimatedTranslationUSD':sum(f['estimatedUSD'] for f in fees),
            'note':'Earlier textModelRequests=0 and blocked limitation refer to the preceding paired-English phase.'}
        write_json(decision_path,decision)
    print('REPORT '+str(DEST/'report.json'),flush=True)


if __name__=='__main__':main()
