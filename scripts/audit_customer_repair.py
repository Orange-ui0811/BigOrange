"""Read-only verification of synthetic run conditions and citations against current local B chunks."""
import json
from pathlib import Path
from customer_local_smoke import sql
from run_analysis_customer_b_compare import definitions

OUT=Path(__file__).resolve().parents[1]/'docs/customer/repair-v411-live'

def main():
    cases={c['id']:c for c in definitions()}
    rows=[json.loads(p.read_text(encoding='utf-8')) for p in sorted(OUT.glob('P*-*-customerB.json'))]
    refs=[ref for row in rows for ref in row.get('result',{}).get('references',[])]
    ids=sorted({int(ref['chunkId']) for ref in refs})
    chunks={}
    if ids:
        raw=sql("SELECT JSON_ARRAYAGG(JSON_OBJECT('id',c.id,'documentId',c.document_id,'versionId',c.version_id,'content',c.content,'status',d.status,'activeVersion',d.active_version_id,'deleted',d.deleted_at IS NOT NULL)) FROM rag_b_chunk c JOIN rag_b_document d ON d.id=c.document_id WHERE c.id IN ("+','.join(map(str,ids))+")")
        chunks={c['id']:c for c in json.loads(raw)}
    errors=[]
    for row in rows:
        result=row.get('result',{});state=result.get('state',{});key=row['case']
        if row['run']['status']!='SUCCEEDED':errors.append([key,'RUN_NOT_SUCCESSFUL'])
        for field,value in cases[key]['hard'].items():
            if state.get(field)!=value:errors.append([key,'STATE_MISMATCH',field])
        if state.get('pendingConditions'):errors.append([key,'UNEXPECTED_PENDING'])
        if not row.get('sessionSoftDeleted'):errors.append([key,'TEST_SESSION_NOT_CLEANED'])
        if row.get('metrics',{}).get('promptVersion')!='customer-v4.11-grounded-recommendations':errors.append([key,'WRONG_VERSION'])
        for call in row.get('metrics',{}).get('modelCalls',[]):
            if call.get('phase') in ('EVIDENCE','VERIFY'):
                if call.get('thinking') is not False:errors.append([key,'EVIDENCE_THINKING_POLICY'])
                if call.get('usage',{}).get('completion_tokens_details',{}).get('reasoning_tokens',0)>0:errors.append([key,'UNEXPECTED_EVIDENCE_REASONING_TOKENS'])
        if key=='P08_unavailable' and (state.get('verificationMode')!='STRICT' or state.get('allowHardware') is not False or result.get('projects')):errors.append([key,'STRICT_UNAVAILABLE_GUARD'])
        for card in result.get('projects',[]):
            language=card.get('language')
            if language and language.lower()!=state.get('language','').lower():errors.append([key,'KNOWN_LANGUAGE_CONFLICT',card['name'],language])
            if card.get('name','').lower()=='codecrafters-io/build-your-own-x':errors.append([key,'KNOWN_GUIDE_COLLECTION_AS_PROJECT'])
            if card.get('importable') and (card.get('matchStatus')=='NEEDS_VERIFICATION' or not card.get('references')):errors.append([key,'UNSUPPORTED_IMPORT',card['name']])
            for ref in card.get('references',[]):
                source=ref.get('sourceRef','').split('|')[0].lower()
                allowed={card.get('name','').lower(),card.get('sourceRef','').lower()}
                if card.get('type') in ('BEGINNER_PROJECT','CAREER_PROJECT'):allowed.update([f"beginner:{card['id']}",f"career:{card['id']}"])
                if source not in allowed:errors.append([key,'CROSS_PROJECT_CITATION',card['name']])
        for ref in result.get('references',[]):
            c=chunks.get(int(ref['chunkId']))
            if not c or c['documentId']!=ref['documentId'] or c['versionId']!=ref['versionId'] or ref['quote'] not in c['content']:
                errors.append([key,'CITATION_NOT_IN_DATABASE',ref['chunkId']]);continue
            if c['status']!='READY' or c['deleted'] or c['activeVersion']!=c['versionId']:errors.append([key,'INACTIVE_CITATION',ref['chunkId']])
            spans=[s for s in ref.get('spans',[]) if s['text']==ref['quote']]
            encoded=c['content'].encode('utf-16-le')
            if not any(encoded[s['startInChunk']*2:s['endInChunk']*2].decode('utf-16-le')==ref['quote'] for s in spans):errors.append([key,'SPAN_OFFSET_MISMATCH',ref['chunkId']])
    output=dict(runs=len(rows),citations=len(refs),uniqueChunks=len(chunks),errors=errors,
        scope='Actual local DB provenance, saved hard conditions, account test cleanup, strict/no-import boundaries. Does not measure real-world correctness of source documents or all paraphrases.')
    (OUT/'source-audit.json').write_text(json.dumps(output,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(output,ensure_ascii=False,indent=2))
    if len(rows)!=8 or errors:raise SystemExit(1)

if __name__=='__main__':main()
