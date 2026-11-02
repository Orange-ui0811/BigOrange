package com.orangejd.server.foundation.external;

import org.apache.ibatis.annotations.*;

@Mapper
public interface TranslationCacheMapper {
    @Select("""
            SELECT translated_json FROM ai_translation_cache
            WHERE content_hash = #{hash} AND purpose = #{purpose} AND model = #{model}
            """)
    String find(@Param("hash") String hash, @Param("purpose") String purpose, @Param("model") String model);

    @Insert("""
            INSERT INTO ai_translation_cache
                (content_hash, purpose, model, translated_json)
            VALUES (#{hash}, #{purpose}, #{model}, #{translated})
            ON DUPLICATE KEY UPDATE translated_json = VALUES(translated_json),
                last_used_at = NOW(), hit_count = hit_count + 1
            """)
    int save(@Param("hash") String hash, @Param("purpose") String purpose,
             @Param("model") String model, @Param("translated") String translated);

    @Update("""
            UPDATE ai_translation_cache SET last_used_at = NOW(), hit_count = hit_count + 1
            WHERE content_hash = #{hash} AND purpose = #{purpose} AND model = #{model}
            """)
    int touch(@Param("hash") String hash, @Param("purpose") String purpose, @Param("model") String model);
}
