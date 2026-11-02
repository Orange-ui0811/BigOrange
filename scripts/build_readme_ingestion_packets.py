"""Export reversible, typed ingestion packets from a frozen projection (no inference)."""
from collections import Counter
import re
from run_readme_cleaning_experiment import STAGE, OUT, VARIANTS, read, write, sha, norm
from rag_readme_cleaning import PARSER, code_ranges


def utf16_offsets(text):
    result=[0];at=0
    for ch in text:
        at+=2 if ord(ch)>0xffff else 1;result.append(at)
    return result


def block_kind(text,label):
    """This is a navigation hint, never an extracted or verified business fact."""
    if re.search(r'许可|版权|licen[cs]e|copyright',label,re.I):return 'LICENSE'
    if re.search(r'安装|部署|环境|依赖|install|deploy|requirement|prerequisite',label,re.I):return 'SETUP'
    if re.search(r'贡献|contribut|开发指南|development',label,re.I):return 'CONTRIBUTION'
    if re.search(r'特性|功能|概述|介绍|overview|feature|introduction',label,re.I):return 'PURPOSE_FEATURES'
    if re.search(r'风险|注意|警告|limitation|warning|caveat',label,re.I):return 'LIMITATIONS'
    if re.search(r'API|使用|用法|usage|example',label,re.I):return 'USAGE'
    if text.lstrip().startswith(('```','~~~')):return 'CODE_EXAMPLE'
    return 'OTHER'


def main():
    snap=read(STAGE/'snapshot.json');docs={d['id']:d for d in snap['documents']};cases=read(OUT/'dataset.json')['cases']
    checks={};all_manifest=[];code_checks={}
    for variant in VARIANTS:
        packets=read(STAGE/(variant+'-packets.json'));projections={p['documentId']:p for p in packets}
        chunks=[c for c in read(STAGE/(variant+'-chunks.json')) if c['sourceType']=='GITHUB']
        utf={i:utf16_offsets(docs[i]['content']) for i in projections}
        mapped=0;count=0;utf_verified=0;full_cover=0
        for c in chunks:
            packet=projections[c['documentId']];a=c['start'];b=c['end'];spans=[];covered=0
            for s in packet['originalSpans']:
                left=max(a,s['start']);right=min(b,s['end'])
                if right<=left:continue
                if s['originalStart']<0:
                    # Only whitespace may be synthesized by this projection.
                    if packet['projection'][left:right].strip():raise ValueError('Synthesized factual text')
                    covered+=right-left;continue
                oa=s['originalStart']+left-s['start'];ob=oa+right-left;offsets=utf[c['documentId']]
                raw=docs[c['documentId']]['content'];excerpt=raw[oa:ob]
                if excerpt!=c['content'][left-a:right-a]:raise ValueError('Citation roundtrip failed')
                if raw.encode('utf-16-le')[offsets[oa]*2:offsets[ob]*2].decode('utf-16-le')!=excerpt:raise ValueError('UTF16 mismatch')
                spans.append({'cleanStart':left,'cleanEnd':right,'originalStart':oa,'originalEnd':ob,
                    'originalUtf16Start':offsets[oa],'originalUtf16End':offsets[ob],
                    'originalStartLine':raw.count('\n',0,oa)+1,'originalEndLine':raw.count('\n',0,ob)+1})
                utf_verified+=1;covered+=right-left
            if covered!=len(c['content']):raise ValueError('Unmapped chunk text')
            full_cover+=1;mapped+=len(spans);count+=1
            packet.setdefault('chunks',[]).append({'sectionPath':c['section'],'blockTypeHint':block_kind(c['content'],c['section'] or ''),
                'start':a,'end':b,'textSha256':sha(c['content']),'originalRanges':spans})
        preserved=[]
        for case in cases:
            if case['types']!=['GITHUB'] or not case['anchors']:continue
            matching=[p for p in packets if p['sourceRef'].split('|')[0] in case['refs']]
            present=any(all(norm(anchor) in norm(p['projection']) for anchor in case['anchors']) for p in matching)
            chunk_present=any(ch['sourceRef'].split('|')[0] in case['refs'] and all(norm(anchor) in norm(ch['content']) for anchor in case['anchors']) for ch in chunks)
            preserved.append({'key':case['key'],'category':case['category'],'inDocument':present,'inSingleChunk':chunk_present})
        for p in packets:
            p['schemaVersion']='readme-projection-v1';p['variant']=variant
            p['fieldSemantics']={'blockTypeHint':'heading-based navigation only; not a verified fact',
                'originalRanges':'quote original ranges separately; never cite concatenated spans as contiguous original text'}
            flags=p['qualityFlags'];p['proposedRouting']='FOLLOW_SOURCE_POINTER' if 'README_POINTER' in flags else 'RESOURCE_CATALOG' if 'RESOURCE_COLLECTION_HINT' in flags else 'PROJECT_SOURCE'
            p['routingAppliedToExperiment']=False
            p['cardContract']={'projectFacts':'Retrieve supported chunks with originalRanges',
                'personalizedReason':'Compute at query time from confirmed user requirements + supported facts',
                'roleFit':'Requires job/role evidence; absent role facts remain unknown',
                'suggestedWork':'Label as advice; do not claim upstream already implements it',
                'demonstrableSkills':'Specify a proposed artifact and check; never claim user has completed it'}
        if variant in ['light','structured','selective']:
            changed_code=[];protected=0
            repair_ids={s['documentId'] for s in read(OUT/'document-statistics.json') if s['variant']==variant and s['events'].get('unwrap_document')}
            for p in packets:
                original=docs[p['documentId']]['content']
                for a,b in code_ranges(original):
                    if p['documentId'] in repair_ids and not original[:a].strip() and not original[b:].strip():continue
                    protected+=1
                    if original[a:b] not in p['projection']:changed_code.append({'documentId':p['documentId'],'start':a,'end':b})
            code_checks[variant]={'protectedCodeSpans':protected,'changedCodeSpans':changed_code,'wrapperRepairDocuments':sorted(repair_ids)}
        write(STAGE/(variant+'-ingestion-packets.json'),packets)
        checks[variant]={'documents':len(packets),'chunks':count,'fullyMappedChunks':full_cover,'verifiedOriginalRanges':mapped,
            'verifiedUtf16Ranges':utf_verified,'anchorRetention':preserved,
            'lostAnchors':[r['key'] for r in preserved if not r['inDocument']]}
        all_manifest.extend({'variant':variant,'documentId':p['documentId'],'sourceRef':p['sourceRef'],
            'originalSha256':p['originalSha256'],'cleanSha256':p['cleanSha256'],'qualityFlags':p['qualityFlags'],
            'proposedRouting':p['proposedRouting'],'chunks':len(p.get('chunks',[]))} for p in packets)
    write(OUT/'provenance-and-retention.json',checks);write(OUT/'packet-manifest.json',all_manifest)
    write(OUT/'code-preservation.json',code_checks)
    print({k:{n:v for n,v in d.items() if n not in ['anchorRetention','lostAnchors']}|{'lostAnchors':len(d['lostAnchors'])} for k,d in checks.items()})


if __name__=='__main__':main()
