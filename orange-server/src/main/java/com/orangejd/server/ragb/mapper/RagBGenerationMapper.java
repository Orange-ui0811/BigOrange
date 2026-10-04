package com.orangejd.server.ragb.mapper;
import com.orangejd.server.ragb.domain.RagBIndexGeneration;
import org.apache.ibatis.annotations.*;
import java.util.List;
public interface RagBGenerationMapper {
    @Select("SELECT version_id FROM rag_b_generation_document WHERE generation_id=#{generationId} AND document_id=#{documentId}")
    Long selectGenerationVersion(@Param("generationId") Long generationId, @Param("documentId") Long documentId);

    @Insert("INSERT INTO rag_b_generation_document(generation_id,document_id,version_id) VALUES(#{generationId},#{documentId},#{versionId})")
    int insertGenerationDocument(@Param("generationId") Long generationId, @Param("documentId") Long documentId, @Param("versionId") Long versionId);

    @Update("""
            UPDATE rag_b_document d JOIN rag_b_generation_document gd ON gd.document_id=d.id
            JOIN rag_b_document_version v ON v.id=gd.version_id
            SET d.active_version_id=v.id, v.status='READY', v.indexed_generation_id=#{generationId}
            WHERE gd.generation_id=#{generationId} AND d.status='READY' AND d.deleted_at IS NULL
            """)
    int publishGenerationVersions(@Param("generationId") Long generationId);
    @Select("""
            SELECT id, generation_key, redis_prefix, adapter_type, embedding_model, embedding_dimensions, chunk_policy, status, active, document_count, chunk_count,
                   last_error, activated_at, create_time, update_time
            FROM rag_b_index_generation
            WHERE active = 1 AND status = 'ACTIVE'
            ORDER BY activated_at DESC, id DESC
            LIMIT 1
            """)
    RagBIndexGeneration selectActiveGeneration();

    @Insert("""
            INSERT INTO rag_b_index_generation
                (generation_key, redis_prefix, adapter_type, status, active, embedding_model, embedding_dimensions, chunk_policy)
            VALUES
                (#{generationKey}, #{redisPrefix}, #{adapterType}, #{status}, 0, #{embeddingModel}, #{embeddingDimensions}, #{chunkPolicy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertGeneration(RagBIndexGeneration generation);

    @Select("""
            SELECT id, generation_key, redis_prefix, adapter_type, embedding_model, embedding_dimensions, chunk_policy, status, active, document_count, chunk_count,
                   last_error, activated_at, create_time, update_time
            FROM rag_b_index_generation WHERE id = #{id}
            """)
    RagBIndexGeneration selectGeneration(@Param("id") Long id);

    @Select("""
            SELECT id, generation_key, redis_prefix, adapter_type, embedding_model, embedding_dimensions, chunk_policy, status, active, document_count, chunk_count,
                   last_error, activated_at, create_time, update_time
            FROM rag_b_index_generation ORDER BY id DESC LIMIT #{limit}
            """)
    List<RagBIndexGeneration> selectGenerations(@Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM rag_b_index_generation WHERE status = 'BUILDING'")
    int countBuildingGenerations();

    @Update("UPDATE rag_b_index_generation SET status = 'BUILDING', last_error = NULL WHERE id = #{id} AND active = 0")
    int markGenerationBuilding(@Param("id") Long id);

    @Insert("""
            INSERT INTO rag_b_generation_chunk (generation_id, chunk_id, vector_key, index_status)
            VALUES (#{generationId}, #{chunkId}, #{vectorKey}, 'INDEXED')
            ON DUPLICATE KEY UPDATE vector_key = VALUES(vector_key), index_status = 'INDEXED'
            """)
    int upsertGenerationChunk(@Param("generationId") Long generationId,
                              @Param("chunkId") Long chunkId,
                              @Param("vectorKey") String vectorKey);

    @Select("SELECT COUNT(*) FROM rag_b_generation_chunk WHERE generation_id = #{generationId} AND index_status = 'INDEXED'")
    int countGenerationChunks(@Param("generationId") Long generationId);

    @Update("""
            UPDATE rag_b_index_generation
            SET status = 'READY', document_count = #{documentCount}, chunk_count = #{chunkCount}, last_error = NULL
            WHERE id = #{id} AND active = 0
            """)
    int markGenerationReady(@Param("id") Long id, @Param("documentCount") int documentCount,
                            @Param("chunkCount") int chunkCount);

    @Update("""
            UPDATE rag_b_index_generation
            SET status = 'FAILED', active = 0, last_error = LEFT(#{error}, 2000)
            WHERE id = #{id} AND active = 0
            """)
    int markGenerationFailed(@Param("id") Long id, @Param("error") String error);

    @Update("UPDATE rag_b_index_generation SET active = 0, status = 'RETIRED' WHERE active = 1")
    int retireActiveGeneration();

    @Update("""
            UPDATE rag_b_index_generation
            SET active = 1, status = 'ACTIVE', activated_at = NOW(3)
            WHERE id = #{id} AND status = 'READY' AND active = 0
            """)
    int activateGeneration(@Param("id") Long id);

    @Update("""
            UPDATE rag_b_chunk c
            JOIN rag_b_generation_chunk gc ON gc.chunk_id = c.id AND gc.generation_id = #{generationId}
            SET c.vector_key = gc.vector_key, c.index_status = 'INDEXED'
            """)
    int publishGenerationChunkKeys(@Param("generationId") Long generationId);

}
