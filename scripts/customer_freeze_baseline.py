"""Freeze code hashes and data/index counts without exporting document contents or secrets."""
import hashlib
import json
import subprocess
from datetime import datetime, timezone
from customer_local_smoke import sql, ROOT

def main():
    files = sorted((ROOT / 'orange-server/src/main/java/com/orangejd/server/customer').glob('*.java'))
    files += [ROOT / 'orange-server/src/main/resources/db/migration/V41__add_customer_service.sql', ROOT / 'docs/customer/cases-v1.json']
    files += [ROOT / path for path in (
        'orange-server/src/main/java/com/orangejd/server/controller/app/AppCustomerController.java',
        'orange-server/src/main/java/com/orangejd/server/controller/admin/AdminCustomerKnowledgeController.java',
        'orange-server/src/main/java/com/orangejd/server/foundation/external/ExternalAiClient.java',
        'orange-server/src/main/java/com/orangejd/server/service/TrendContextBuilder.java',
        'orange-server/src/main/resources/application.yml',
        'orange-web/src/api/customer.js',
        'orange-web/src/composables/useCustomerChat.js',
        'orange-web/src/components/CustomerChat.vue')]
    hashes = {path.relative_to(ROOT).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest() for path in files}
    source_rows = sql('SELECT id,source,SHA2(COALESCE(original_content,content,\'\'),256) FROM knowledge_document ORDER BY id')
    report = {'frozenAtUtc': datetime.now(timezone.utc).isoformat(), 'gitHead': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'workingTreeIsClean': not bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT, text=True).strip()), 'codeHashes': hashes,
        'legacyDocumentSnapshotSha256': hashlib.sha256(source_rows.encode()).hexdigest(),
        'legacyDocumentsBySource': sql('SELECT source,COUNT(*),SUM(original_content IS NOT NULL AND LENGTH(original_content)>0) FROM knowledge_document GROUP BY source'),
        'activeGeneration': sql('SELECT generation_key,embedding_model,embedding_dimensions,chunk_policy,adapter_type,document_count,chunk_count FROM rag_b_index_generation WHERE active=1'),
        'bDocumentsByStatus': sql('SELECT status,COUNT(*) FROM rag_b_document GROUP BY status'),
        'publishedProjectDataQuality': sql("SELECT COUNT(*),SUM(primary_language IS NULL),SUM(requires_docker IS NULL),SUM(requires_database IS NULL),SUM(requires_secrets IS NULL) FROM beginner_project WHERE status='PUBLISHED'"),
        'publishedLanguages': sql("SELECT primary_language,COUNT(*) FROM beginner_project WHERE status='PUBLISHED' GROUP BY primary_language"),
        'customerWhitelist': [5, 1], 'costStatus': 'UNKNOWN', 'releaseApproved': False}
    path = ROOT / 'docs/customer/acceptance/baseline.json'
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({key: report[key] for key in ('activeGeneration', 'legacyDocumentsBySource', 'publishedProjectDataQuality', 'publishedLanguages')}, ensure_ascii=False))

if __name__ == '__main__':
    main()
