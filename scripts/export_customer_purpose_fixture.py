"""Export public project data only for an offline Java regression; no account data or API calls."""
import json
from customer_local_smoke import ROOT, sql

rows = sql("""SELECT JSON_OBJECT('id',d.id,'source_ref',d.source_ref,'title',d.title,
 'active_version_id',d.active_version_id,'topic_text',LEFT(v.original_content,12000))
 FROM rag_b_document d JOIN rag_b_document_version v ON v.id=d.active_version_id AND v.document_id=d.id
 WHERE d.source_type='GITHUB' AND d.status='READY' AND d.deleted_at IS NULL
 AND NOT EXISTS (SELECT 1 FROM beginner_project bp WHERE LOWER(bp.full_name)=LOWER(SUBSTRING_INDEX(d.source_ref,'|',1))
 AND (bp.status IN ('HIDDEN','INVALID') OR COALESCE(bp.archived,0)=1))
 AND NOT EXISTS (SELECT 1 FROM career_project_profile cp JOIN beginner_project bp ON bp.id=cp.project_id
 WHERE LOWER(bp.full_name)=LOWER(SUBSTRING_INDEX(d.source_ref,'|',1)) AND cp.status IN ('HIDDEN','INVALID'))
 ORDER BY d.update_time DESC,d.id DESC""")
data = {'documents': [json.loads(row) for row in rows.splitlines()]}
path = ROOT / '.tmp/customer-v418-source-fixture.json'
path.parent.mkdir(exist_ok=True)
path.write_text(json.dumps(data, ensure_ascii=False), encoding='utf-8')
print(json.dumps({'documents': len(data['documents']), 'path': str(path), 'externalCalls': 0}))
