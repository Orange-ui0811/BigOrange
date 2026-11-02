"""Read-only final corpus/implementation drift check for the authorized A/B run."""
import hashlib,json
from datetime import datetime
from pathlib import Path
from export_rag_model_corpus import query
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/rag-b/trend-ab-20261009'
before=json.loads((OUT/'before.json').read_text(encoding='utf-8'))
after={
 'documentsA':query("SELECT JSON_OBJECT('id',id,'source',source,'status',vector_status,'hash',SHA2(CONCAT_WS('|',COALESCE(original_content,''),COALESCE(content,'')),256)) FROM knowledge_document ORDER BY id"),
 'documentsB':query("SELECT JSON_OBJECT('id',id,'source',source_type,'status',status,'version',active_version_id,'hash',content_hash) FROM rag_b_document WHERE deleted_at IS NULL ORDER BY id"),
 'generation':query("SELECT JSON_OBJECT('id',id,'generation',generation_key,'model',embedding_model,'provider',embedding_provider,'policy',chunk_policy,'documents',document_count,'chunks',chunk_count) FROM rag_b_index_generation WHERE active=1"),
 'at':datetime.now().astimezone().isoformat()}
manifest=json.loads((OUT/'manifest.json').read_text(encoding='utf-8'))
source=ROOT/'orange-server/src/main/java/com/orangejd/server/service'
checks={k:before[k]==after[k] for k in ['documentsA','documentsB','generation']}
checks['sourceFilesUnchanged']=all(hashlib.sha256((source/f).read_bytes()).hexdigest()==h for f,h in manifest['codeSha256'].items())
after['unchanged']=checks
(OUT/'after.json').write_text(json.dumps(after,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(checks,ensure_ascii=False))
