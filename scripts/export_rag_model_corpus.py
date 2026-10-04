"""Read-only export of the existing B corpus. Raw text remains in ignored staging."""
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / '_experiment_staging' / 'rag-model-chunk-20261002'

def query(sql):
    command = ['docker', 'exec', 'nxz-bigorange-mysql-1', 'sh', '-c',
               'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names "$MYSQL_DATABASE" -e "$1"',
               'sh', sql]
    result = subprocess.run(command, capture_output=True, check=True, encoding='utf-8')
    return [json.loads(line) for line in result.stdout.splitlines() if line.strip()]

def main():
    documents = query("""SELECT JSON_OBJECT('id',d.id,'sourceType',d.source_type,
        'sourceRef',d.source_ref,'title',d.title,'versionId',v.id,
        'contentHash',v.content_hash,'content',v.original_content)
        FROM rag_b_document d JOIN rag_b_document_version v ON v.id=d.active_version_id
        WHERE d.status='READY' AND v.status='READY' ORDER BY d.id""")
    chunks = query("""SELECT JSON_OBJECT('documentId',c.document_id,'chunkId',c.id,
        'section',c.section_path,'start',c.start_offset,'end',c.end_offset,
        'content',c.content,'metadata',c.metadata_json)
        FROM rag_b_chunk c JOIN rag_b_document d ON d.active_version_id=c.version_id
        WHERE d.status='READY' ORDER BY c.document_id,c.chunk_index""")
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT/'corpus.json').write_text(json.dumps({'documents':documents,'chunks':chunks},ensure_ascii=False),encoding='utf-8')
    print(json.dumps({'documents':len(documents),'chunks':len(chunks),
        'characters':sum(len(d['content']) for d in documents),'snapshot':str(OUT/'corpus.json')},ensure_ascii=False))

if __name__=='__main__':
    main()
