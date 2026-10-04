"""Read-only field-level audit of B contents against legacy knowledge records."""
import json
import sys
from collections import defaultdict
from pathlib import Path

from export_rag_model_corpus import query


def main():
    sys.stdout.reconfigure(encoding='utf-8')
    def hash_(field):
        return "SHA2(REPLACE(REPLACE("+field+",CONCAT(CHAR(13),CHAR(10)),CHAR(10)),CHAR(13),CHAR(10)),256)"
    rows=query("""SELECT JSON_OBJECT('bId',d.id,'sourceType',d.source_type,'sourceRef',d.source_ref,
        'legacyId',k.id,'legacyRef',k.source_ref,'bLength',CHAR_LENGTH(v.original_content),
        'legacyOriginalLength',CHAR_LENGTH(k.original_content),'legacyContentLength',CHAR_LENGTH(k.content),
        'bHash',"""+hash_('v.original_content')+",'legacyOriginalHash',"+hash_('k.original_content')+
        ",'legacyContentHash',"+hash_('k.content')+""",
        'translationStatus',k.translation_status)
        FROM rag_b_document d JOIN rag_b_document_version v ON v.id=d.active_version_id
        LEFT JOIN knowledge_document k ON k.source=d.source_type
          AND (k.source_ref=d.source_ref OR SUBSTRING_INDEX(k.source_ref,'|',1)=d.source_ref)
        WHERE d.status='READY' ORDER BY d.id,k.id""")
    grouped=defaultdict(list)
    for row in rows:grouped[row['bId']].append(row)
    summaries=defaultdict(lambda:defaultdict(int));details=[]
    for b_id,candidates in grouped.items():
        original=[r for r in candidates if r['bHash']==r['legacyOriginalHash']]
        content=[r for r in candidates if r['bHash']==r['legacyContentHash']]
        matched=[r for r in candidates if r['legacyId'] is not None]
        row=(original or content or matched or candidates)[0]
        category=('original_and_content_identical' if original and any(
                    r['bHash']==r['legacyContentHash'] for r in original)
                  else 'matches_legacy_original_only' if original
                  else 'matches_legacy_content_only' if content
                  else 'legacy_found_but_content_differs' if matched
                  else 'no_legacy_source_match')
        summaries[row['sourceType']][category]+=1
        details.append({**row,'comparison':category,'matchingLegacyCandidates':len(matched)})
    report={'method':'Read-only comparison of LF-normalized SHA256 of active B version original_content '
            'with legacy original_content and content; exact or period-stripped source reference matches.',
        'documents':len(grouped),'summary':dict(summaries),'details':details,
        'limits':['Equal original/content does not prove whether upstream business fields were translated.',
                  'Translation status DONE alone is not proof of a translation call.']}
    path=Path('docs/rag-b/content-origin-audit.json')
    path.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'documents':report['documents'],'summary':report['summary']},ensure_ascii=False,indent=2))
    print('GITHUB',json.dumps([r for r in details if r['sourceType']=='GITHUB'],ensure_ascii=False))


if __name__=='__main__':main()
