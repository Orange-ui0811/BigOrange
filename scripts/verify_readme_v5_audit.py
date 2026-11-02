"""Read-only verification of the V5 audit and blocked pilot draft; --seal finalizes evidence once."""
import argparse
import hashlib
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
from prepare_readme_v5_audit import ROOT,Q,OUT,STAGE,PILOT,read,sha,digest,coverage


def write_new(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    with path.open('x',encoding='utf-8') as handle:handle.write(json.dumps(value,ensure_ascii=False,indent=2)+'\n')


def main(seal=False):
    sealfile=OUT/'audit-seal.json';snapshotfile=OUT/'source-snapshot.json'
    if sealfile.exists():
        for name,value in read(sealfile)['files'].items():assert sha(ROOT/name)==value,'Sealed audit changed: '+name
    snapshot=read(snapshotfile) if snapshotfile.exists() else {}
    current_sources=True
    protected=read(OUT/'before.json')['protectedFiles']
    for name,value in {**protected,**read(STAGE/'plan-inputs.json')}.items():
        path=ROOT/name
        if path.exists() and sha(path)==value:continue
        backup=snapshot.get(name)
        assert backup and backup['sha256']==value and sha(ROOT/backup['archive'])==value,'Protected input changed without matching snapshot: '+name
        current_sources=False
    plans=read(STAGE/'all-plans.json');audit=read(OUT/'audit.json')
    assert plans['providerCalls']==plans['indexWrites']==audit['providerCalls']==audit['indexWrites']==0
    assert len(plans['documents'])==12 and len(audit['cases'])==29
    docs={d['source']['documentId']:d for d in plans['documents']}
    blocks={b['blockId']:b for d in docs.values() for b in d['preview']['blocks']}
    sources=read(Q/'r4/sources.json')['documents']
    originals={b['blockId']:b for s in sources for b in s['blocks']}
    assert set(blocks)==set(originals)
    for doc in docs.values():
        assert digest(doc['source']['text'])==doc['sourceSha256']
        raw=doc['source']['text'].encode('utf-16-le')
        for block in doc['preview']['blocks']:
            assert raw[block['startUtf16']*2:block['endUtf16']*2].decode('utf-16-le')==block['text']
            assert digest(block['text'])==block['sha256']
    bycase={c['caseId']:c for c in audit['cases']};assert len(bycase)==29
    for c in audit['cases']:
        assert c['sourceBlock']==originals[c['blockId']]
        for variant in docs[c['documentId']]['variants']:
            assert c['newPlanCoverage'][variant['name']]==coverage(docs[c['documentId']],c['blockId'],variant)
    labels=read(OUT/'reviewed-labels-v3.json');assert len(labels['cases'])==149
    old=read(Q/'r4/frozen-dataset.json');bylabel={c['caseId']:c for c in labels['cases']}
    for c in old['cases']:
        new=bylabel[c['caseId']]
        assert new['block']==c['block'] and new['role']==c['role']
        expected=dict(new['expected'])
        if c['caseId']=='Q2-240-52':expected['rationale']=c['expected']['rationale']
        assert expected==c['expected'],'Undeclared original-label change'
    for c in labels['cases']:assert c['block']==originals[c['block']['blockId']]
    subset=read(PILOT/'frozen-labels.json');expected_ids={c['block']['blockId'] for c in subset['cases']}
    requests=read(PILOT/'requests.json')['batches'];proposal=read(PILOT/'execution-proposal.json')
    provider=read(PILOT/'provider-config.json')
    assert len(requests)==proposal['primaryRequests']==13
    assert proposal['reserveRequests']==2 and proposal['maximumProposedAttempts']==15
    assert not proposal['authorized'] and not proposal['readyForExecution'] and proposal['requiresReplanBeforeAuthorization']
    assert not (PILOT/'provider-calls.jsonl').exists() and not (PILOT/'authorization.json').exists()
    assert len(set(b['requestId'] for b in requests))==len(set(b['cacheKey'] for b in requests))==len(requests)
    for b in requests:
        variant=next(v for v in docs[b['documentId']]['variants'] if v['maxBlocks']==8)
        actual=next(x for x in variant['batches'] if x['cacheKey']==b['cacheKey'])
        assert all(actual[k]==b[k] for k in actual),'Pilot input differs from actual Java plan'
        assert set(b['targetIds'])<=expected_ids
        assert len(b['targetIds'])<=8 and b['inputUpperBound']<=12000 and b['outputTokens']==2048
        assert digest(b['system'])==b['systemSha256'] and digest(b['user'])==b['userSha256']
        user=json.loads(b['user'])
        for row in user['targets']+user['context']:assert row['raw']==blocks[row['blockId']]['text']
        versions=plans['versions']
        key='\n'.join([versions['selectionPolicy'],versions['prompt'],versions['schema'],versions['projectionPolicy'],versions['rulePolicy'],
                       versions['parser'],provider['modelIdentity'],'2048',b['system'],b['user']])
        assert digest(key)==b['cacheKey']
    assert proposal['firstPassReservedUpperBound']==sum(b['inputUpperBound']+2048 for b in requests)
    assert proposal['maximumReservedUpperBound']==15*(12000+2048)
    suitepath=ROOT/'orange-server/target/surefire-reports/TEST-com.orangejd.server.ragb.ingest.K3V5AuditPlanTest.xml'
    suite_source=ROOT/snapshot[suitepath.relative_to(ROOT).as_posix()]['archive'] if snapshotfile.exists() else suitepath
    suite=ET.parse(suite_source).getroot();assert int(suite.attrib['tests'])==1
    assert all(int(suite.attrib[n])==0 for n in ['failures','errors','skipped'])
    assert 'BUILD SUCCESS' in (STAGE/'planning-tests.log').read_text(encoding='utf-8-sig')
    if seal and not sealfile.exists():
        assert current_sources,'Cannot seal against already changed live inputs'
        paths={name for name in {**protected,**read(STAGE/'plan-inputs.json')}
               if name.endswith('.java') and name.startswith('orange-') or name.startswith('scripts/') and name.endswith('.py')
               or name.endswith('/pom.xml') or name=='pom.xml'}
        paths|={suitepath.relative_to(ROOT).as_posix(),Path(__file__).resolve().relative_to(ROOT).as_posix(),
                'scripts/prepare_readme_v5_audit.py'}
        for name in sorted(paths):
            assert '..' not in Path(name).parts and not Path(name).is_absolute()
            target=OUT/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True)
            if target.exists():assert sha(target)==sha(ROOT/name)
            else:target.write_bytes((ROOT/name).read_bytes())
            snapshot[name]={'sha256':sha(ROOT/name),'archive':target.relative_to(ROOT).as_posix()}
        write_new(snapshotfile,snapshot)
        files=[p for d in [OUT,PILOT] for p in d.rglob('*') if p.is_file() and p.name not in {'audit-seal.json','integrity-check.json'}]
        files += [STAGE/'all-plans.json',STAGE/'plan-inputs.json',STAGE/'planning-tests.log',Path(__file__).resolve(),ROOT/'scripts/prepare_readme_v5_audit.py']
        write_new(sealfile,{'status':'AUDIT_LABEL_REVIEW_AND_BLOCKED_PILOT_DRAFT_SEALED',
                            'files':{p.relative_to(ROOT).as_posix():sha(p) for p in files},'providerCalls':0,'pilotAuthorized':False})
    report={'status':'V5_AUDIT_VERIFIED','casesAudited':29,'originalCases':147,'reviewedCases':149,'planningTestsPassed':1,
            'sourceMappingAndPlannerInputsVerified':True,'modelCacheKeysVerified':True,'allPilotTargetsLabelled':True,
            'historicalProtectedFiles':len(protected),'currentSourceMatchesAuditSnapshot':current_sources,
            'newProviderCalls':0,'indexWrites':0,'pilotDraftRequests':13,'pilotDraftReserve':2,'pilotAuthorized':False,
            'pilotReadyForExecution':False,'sealed':sealfile.exists()}
    (OUT/'integrity-check.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False))


if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8');parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--seal',action='store_true')
    main(parser.parse_args().seal)
