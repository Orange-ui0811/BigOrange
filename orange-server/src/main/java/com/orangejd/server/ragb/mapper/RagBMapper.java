package com.orangejd.server.ragb.mapper;
import com.orangejd.server.ragb.domain.RagBChunk;
import com.orangejd.server.ragb.domain.RagBDocument;
import com.orangejd.server.ragb.domain.RagBDocumentVersion;
import com.orangejd.server.ragb.domain.RagBExperimentRun;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;
import java.util.Map;
@Mapper
public interface RagBMapper extends RagBGenerationMapper {
    @Insert("""
            INSERT INTO rag_b_document
                (source_type, source_ref, title, status, content_hash)
            VALUES
                (#{sourceType}, #{sourceRef}, #{title}, #{status}, #{contentHash})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertDocument(RagBDocument document);
    @Insert("""
            INSERT INTO rag_b_document_version
                (document_id, version_no, content_hash, original_content, translated_content, status, chunk_count)
            VALUES
                (#{documentId}, #{versionNo}, #{contentHash}, #{originalContent}, #{translatedContent},
                 #{status}, #{chunkCount})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertVersion(RagBDocumentVersion version);
    @Insert("""
            INSERT INTO rag_b_chunk
                (document_id, version_id, chunk_index, section_path, start_offset, end_offset,
                 content, metadata_json, index_status)
            VALUES
                (#{documentId}, #{versionId}, #{chunkIndex}, #{sectionPath}, #{startOffset}, #{endOffset},
                 #{content}, #{metadataJson}, #{indexStatus})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertChunk(RagBChunk chunk);
    @Select("""
            SELECT id, source_type, source_ref, title, status, active_version_id, content_hash,
                   retry_count, last_error, deleted_at, create_time, update_time
            FROM rag_b_document
            WHERE id = #{id}
            """)
    RagBDocument selectDocument(@Param("id") Long id);
    @Select("""
            SELECT id, source_type, source_ref, title, status, active_version_id, content_hash,
                   retry_count, last_error, deleted_at, create_time, update_time
            FROM rag_b_document
            WHERE source_type = #{sourceType} AND source_ref = #{sourceRef}
            """)
    RagBDocument selectDocumentBySource(@Param("sourceType") String sourceType,
                                        @Param("sourceRef") String sourceRef);
    @Select("""
            SELECT id FROM rag_b_document
            WHERE status='READY' AND deleted_at IS NULL AND active_version_id IS NOT NULL
              AND ((source_type=#{type} AND source_ref=#{source})
                OR (source_type='GITHUB' AND (source_ref=#{repository}
                    OR LEFT(source_ref,CHAR_LENGTH(#{repository})+1)=CONCAT(#{repository},'|'))))
            ORDER BY CASE WHEN source_type=#{type} AND source_ref=#{source} THEN 0 ELSE 1 END,
                     update_time DESC,id DESC LIMIT 12
            """)
    List<Long> selectProjectEvidenceDocuments(@Param("type") String type,@Param("source") String source,@Param("repository") String repository);
    @Select("""
            SELECT id, source_type, source_ref, title, status, active_version_id, content_hash,
                   retry_count, last_error, deleted_at, create_time, update_time
            FROM rag_b_document
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    List<RagBDocument> selectDocuments(@Param("limit") int limit);
    @Select("""
            SELECT id, source_type, source_ref, title, status, active_version_id, content_hash,
                   retry_count, last_error, deleted_at, create_time, update_time
            FROM rag_b_document
            WHERE source_type = 'GITHUB' AND status != 'DELETED' AND deleted_at IS NULL
            ORDER BY update_time ASC, id ASC
            LIMIT #{limit}
            """)
    List<RagBDocument> selectGithubSyncDocuments(@Param("limit") int limit);
    @Select("""
            SELECT id, document_id, version_no, content_hash, original_content, translated_content,
                   status, chunk_count, indexed_generation_id, last_error, create_time, update_time
            FROM rag_b_document_version
            WHERE id = #{id}
            """)
    RagBDocumentVersion selectVersion(@Param("id") Long id);

    @Select("""
            SELECT id, document_id, version_no, content_hash, original_content, translated_content,
                   status, chunk_count, indexed_generation_id, last_error, create_time, update_time
            FROM rag_b_document_version
            WHERE document_id = #{documentId}
            ORDER BY version_no DESC
            LIMIT 1
            """)
    RagBDocumentVersion selectLatestVersion(@Param("documentId") Long documentId);

    @Select("SELECT COALESCE(MAX(version_no), 0) FROM rag_b_document_version WHERE document_id = #{documentId}")
    int selectMaxVersionNo(@Param("documentId") Long documentId);

    @Update("""
            UPDATE rag_b_document
            SET title = #{title}, content_hash = #{contentHash}, last_error = NULL,
                status = CASE
                    WHEN active_version_id IS NULL THEN 'PENDING'
                    WHEN status = 'DELETED' THEN 'READY'
                    ELSE status
                END,
                deleted_at = NULL
            WHERE id = #{documentId}
            """)
    int observeSourceUpdate(@Param("documentId") Long documentId,
                            @Param("title") String title,
                            @Param("contentHash") String contentHash);

    @Select("""
            SELECT id, document_id, version_id, chunk_index, section_path, start_offset, end_offset,
                   content, metadata_json, vector_key, index_status, create_time, update_time
            FROM rag_b_chunk
            WHERE version_id = #{versionId}
            ORDER BY chunk_index
            """)
    List<RagBChunk> selectChunks(@Param("versionId") Long versionId);

    @Select("""
            SELECT id, document_id, version_id, chunk_index, section_path, start_offset, end_offset,
                   content, metadata_json, vector_key, index_status, create_time, update_time
            FROM rag_b_chunk WHERE id = #{id}
            """)
    RagBChunk selectChunk(@Param("id") Long id);

    @Update("UPDATE rag_b_chunk SET metadata_json = #{metadataJson} WHERE id = #{chunkId}")
    int updateChunkMetadata(@Param("chunkId") Long chunkId, @Param("metadataJson") String metadataJson);

    @Select("""
            SELECT primary_language AS language, tech_stack AS techStack, difficulty,
                   estimated_duration AS duration,
                   requires_docker AS requiresDocker, requires_database AS requiresDatabase,
                   requires_secrets AS requiresSecrets, html_url AS repositoryUrl,
                   CAST(id AS CHAR) AS businessId
            FROM beginner_project WHERE id = #{id}
            """)
    Map<String, Object> selectBeginnerBusinessMetadata(@Param("id") Long id);

    @Select("""
            SELECT p.primary_language AS language, p.tech_stack AS techStack, c.difficulty,
                   c.estimated_duration AS duration,
                   p.requires_docker AS requiresDocker, p.requires_database AS requiresDatabase,
                   p.requires_secrets AS requiresSecrets, p.html_url AS repositoryUrl,
                   CAST(p.id AS CHAR) AS businessId
            FROM beginner_project p JOIN career_project_profile c ON c.project_id = p.id
            WHERE p.id = #{id}
            """)
    Map<String, Object> selectCareerBusinessMetadata(@Param("id") Long id);

    @Select("""
            SELECT CONCAT_WS(' ', position, direction, content, display_content)
            FROM job_posting WHERE id = #{id} AND audit_status = 'APPROVED'
            """)
    String selectJobBusinessText(@Param("id") Long id);

    @Select("""
            SELECT id, source_type, source_ref, title, status, active_version_id, content_hash,
                   retry_count, last_error, deleted_at, create_time, update_time
            FROM rag_b_document
            WHERE status = 'READY' AND active_version_id IS NOT NULL AND deleted_at IS NULL
            ORDER BY id
            """)
    List<RagBDocument> selectReadyDocuments();

    @Update("""
            UPDATE rag_b_document d
            JOIN rag_b_document_version v ON v.document_id = d.id
            SET d.status = CASE WHEN d.active_version_id IS NULL THEN 'PROCESSING' ELSE d.status END,
                d.last_error = NULL,
                v.status = 'PROCESSING', v.last_error = NULL
            WHERE d.id = #{documentId} AND v.id = #{versionId}
              AND d.status != 'DELETED'
            """)
    int markProcessing(@Param("documentId") Long documentId, @Param("versionId") Long versionId);

    @Update("""
            UPDATE rag_b_chunk
            SET vector_key = #{vectorKey}, index_status = 'INDEXED'
            WHERE id = #{chunkId} AND version_id = #{versionId}
            """)
    int markChunkIndexed(@Param("chunkId") Long chunkId,
                         @Param("versionId") Long versionId,
                         @Param("vectorKey") String vectorKey);

    @Select("""
            SELECT gc.vector_key
            FROM rag_b_generation_chunk gc
            JOIN rag_b_chunk c ON c.id = gc.chunk_id
            WHERE c.document_id = #{documentId}
            """)
    List<String> selectAllGenerationVectorKeys(@Param("documentId") Long documentId);

    @Update("""
            UPDATE rag_b_document_version
            SET status = 'READY', indexed_generation_id = #{generationId}, last_error = NULL
            WHERE id = #{versionId} AND document_id = #{documentId}
            """)
    int markVersionReady(@Param("documentId") Long documentId,
                         @Param("versionId") Long versionId,
                         @Param("generationId") Long generationId);

    @Update("""
            UPDATE rag_b_document
            SET status = 'READY', active_version_id = #{versionId}, last_error = NULL
            WHERE id = #{documentId} AND status != 'DELETED'
            """)
    int publishVersion(@Param("documentId") Long documentId, @Param("versionId") Long versionId);

    @Update("""
            UPDATE rag_b_document d
            JOIN rag_b_document_version v ON v.document_id = d.id
            SET d.status = CASE WHEN d.active_version_id IS NULL THEN 'FAILED' ELSE d.status END,
                d.last_error = LEFT(#{error}, 2000),
                v.status = 'FAILED', v.last_error = LEFT(#{error}, 2000)
            WHERE d.id = #{documentId} AND v.id = #{versionId}
              AND d.status != 'DELETED'
            """)
    int markFailed(@Param("documentId") Long documentId,
                   @Param("versionId") Long versionId,
                   @Param("error") String error);

    @Update("""
            UPDATE rag_b_document
            SET status = 'PENDING', retry_count = retry_count + 1, last_error = NULL
            WHERE id = #{documentId} AND status = 'FAILED'
            """)
    int prepareRetry(@Param("documentId") Long documentId);

    @Update("""
            UPDATE rag_b_document_version
            SET status = 'PENDING', last_error = NULL
            WHERE id = #{versionId} AND document_id = #{documentId} AND status = 'FAILED'
            """)
    int prepareVersionRetry(@Param("documentId") Long documentId, @Param("versionId") Long versionId);

    @Update("""
            UPDATE rag_b_document
            SET status = 'DELETED', deleted_at = NOW(3), last_error = NULL
            WHERE id = #{documentId} AND status != 'DELETED'
            """)
    int tombstone(@Param("documentId") Long documentId);

    @Update("""
            UPDATE rag_b_chunk
            SET index_status = 'DELETED'
            WHERE document_id = #{documentId} AND index_status != 'DELETED'
            """)
    int tombstoneChunks(@Param("documentId") Long documentId);

    @Select("""
            SELECT vector_key FROM rag_b_chunk
            WHERE document_id = #{documentId} AND vector_key IS NOT NULL
            """)
    List<String> selectVectorKeys(@Param("documentId") Long documentId);

    @Insert("""
            INSERT INTO rag_b_experiment_run
                (run_key, mode, dataset_version, model_snapshot, retrieval_snapshot, status)
            VALUES
                (#{runKey}, #{mode}, #{datasetVersion}, #{modelSnapshot}, #{retrievalSnapshot}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertExperiment(RagBExperimentRun run);

    @Update("""
            UPDATE rag_b_experiment_run
            SET status = #{status}, result_json = #{resultJson}, last_error = #{lastError}
            WHERE id = #{id}
            """)
    int completeExperiment(RagBExperimentRun run);

    @Select("""
            SELECT id, run_key, mode, dataset_version, model_snapshot, retrieval_snapshot,
                   status, result_json, last_error, create_time, update_time
            FROM rag_b_experiment_run
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    List<RagBExperimentRun> selectExperiments(@Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM job_posting WHERE id = #{id} AND audit_status = 'APPROVED'")
    int countApprovedJob(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM beginner_project WHERE id = #{id} AND status = 'PUBLISHED'")
    int countPublishedBeginnerProject(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM career_project_profile WHERE project_id = #{id} AND status = 'PUBLISHED'")
    int countPublishedCareerProject(@Param("id") Long id);
}
