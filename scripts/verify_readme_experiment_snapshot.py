from export_rag_model_corpus import query
from run_readme_cleaning_experiment import write,read,OUT,STAGE,sha
import json

if __name__=='__main__':
    rows=query("SELECT JSON_OBJECT('id',id,'sourceRef',source_ref,'title',title,'effectiveCharacters',CHAR_LENGTH(COALESCE(NULLIF(TRIM(original_content),''),content,''))) FROM knowledge_document WHERE source='GITHUB'")
    write(OUT/'legacy-github-scope.json',{'count':len(rows),'nonempty':sum(r['effectiveCharacters']>0 for r in rows),
        'empty':[r for r in rows if r['effectiveCharacters']==0]})
    docs=query("""SELECT JSON_OBJECT('id',d.id,'sourceType',d.source_type,'sourceRef',d.source_ref,
        'title',d.title,'versionId',v.id,'contentHash',v.content_hash,'content',v.original_content)
        FROM rag_b_document d JOIN rag_b_document_version v ON v.id=d.active_version_id
        WHERE d.status='READY' AND v.status='READY' AND d.deleted_at IS NULL ORDER BY d.id""")
    generation=query("SELECT JSON_OBJECT('id',id,'generation',generation_key,'provider',embedding_provider,'revision',embedding_revision) FROM rag_b_index_generation WHERE active=1")
    before=read(STAGE/'snapshot.json')
    result={'sourceSnapshotUnchanged':docs==before['documents'],'activeGenerationUnchanged':len(generation)==1 and generation[0]['id']==before['generation']['id'],
        'documentCount':len(docs),'legacyGithubCount':len(rows),'legacyGithubEmpty':sum(r['effectiveCharacters']==0 for r in rows)}
    write(OUT/'final-snapshot-check.json',result);print(result)
    assert result['sourceSnapshotUnchanged'] and result['activeGenerationUnchanged']
