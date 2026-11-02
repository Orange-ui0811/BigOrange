from collections import Counter
import json
from pathlib import Path
import numpy as np
from run_readme_cleaning_experiment import ROOT, STAGE, OUT, VARIANTS, read, write, sha, metrics


def main():
    hybrid=read(OUT/'hybrid-summary.json');exact=read(OUT/'exact-summary.json');stats=read(OUT/'document-statistics.json')
    cases=read(OUT/'dataset.json')['cases'];base=read(OUT/'hybrid-raw.json');summary={};differences={}
    for v in VARIANTS:
        selected=[x for x in stats if x['variant']==v];rows=read(OUT/('hybrid-'+v+'.json'))
        summary[v]={'changedDocuments':sum(x['changed'] for x in selected),'githubChars':sum(x['afterChars'] for x in selected),
            'githubEmbeddingTokens':sum(x['tokens'] for x in selected),'githubChunks':sum(x['chunks'] for x in selected),
            'hybrid':hybrid[v],'exact':exact[v]}
        differences[v]=[{'key':a['key'],'question':c['question'],'refs':c['refs'],'beforeRank':a['rank'],'afterRank':b['rank'],
            'beforeHits':a['hits'],'afterHits':b['hits']} for a,b,c in zip(base,rows,cases) if a['rank']!=b['rank']]
        # Cluster bootstrap: paired phrasings and multiple facts in one repository are not independent.
        ids=[i for i,c in enumerate(cases) if c['types']==['GITHUB'] and c['anchors']]
        groups=sorted({cases[i]['refs'][0] for i in ids});bygroup=[[i for i in ids if cases[i]['refs'][0]==g] for g in groups]
        rng=np.random.default_rng(42);deltas=[]
        for _ in range(2000):
            sample=[i for g in rng.integers(0,len(groups),len(groups)) for i in bygroup[g]]
            deltas.append(sum((1/rows[i]['rank'] if rows[i]['rank'] else 0)-(1/base[i]['rank'] if base[i]['rank'] else 0) for i in sample)/len(sample))
        summary[v]['githubClusterBootstrap']={'repositories':len(groups),'cases':len(ids),'samples':2000,'seed':42,
            'MRRDeltaCI95':[float(x) for x in np.percentile(deltas,[2.5,97.5])],
            'note':'Exploratory bootstrap on development/source-authored probes; not blind acceptance.'}
    calls=[json.loads(x) for x in (OUT/'embedding-calls.jsonl').read_text().splitlines()]
    cost={'externalRequests':0,'externalApiYuan':0,'localRequests':len(calls),'embeddedItems':sum(c['items'] for c in calls if c['status']=='OK'),
        'failedAttempts':sum(c['status']!='OK' for c in calls),'providerPromptTokens':sum(c.get('promptEvalCount') or 0 for c in calls),
        'sumLocalRequestMs':sum(c['ms'] for c in calls),'hardwareElectricityYuan':'UNKNOWN','llmAnswerGenerationRequests':0,
        'note':'Experiment compute reuses existing content vectors. Corpus token savings are not end-to-end chat-cost savings.'}
    write(OUT/'comparison.json',{'variants':summary,'cost':cost});write(OUT/'hybrid-paired-changes.json',differences)
    selected_packets=read(STAGE/'selective-ingestion-packets.json');target=STAGE/'selected-documents';target.mkdir(exist_ok=True)
    for p in selected_packets:
        (target/f"document-{p['documentId']}.md").write_text(p['projection'],encoding='utf-8')
    write(OUT/'experiment-code-hashes.json',{name:sha((ROOT/'scripts'/name).read_text(encoding='utf-8')) for name in [
        'rag_readme_cleaning.py','run_readme_cleaning_experiment.py','run_readme_cleaning_hybrid.py','freeze_readme_format_cases.py',
        'build_readme_ingestion_packets.py','verify_readme_experiment_snapshot.py','test_rag_readme_cleaning.py']})
    print(json.dumps({'cost':cost,'selective':summary['selective']['hybrid']['allFrozen'],
        'CI':summary['selective']['githubClusterBootstrap']},ensure_ascii=False))


if __name__=='__main__':main()
