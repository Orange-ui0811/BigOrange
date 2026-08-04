package com.orangejd.server.foundation.aitool;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AiResultRecordMapper {

    @Insert("""
            INSERT INTO ai_result_record
                (request_id, account_id, business_type, business_id, purpose,
                 title, summary, result_json)
            VALUES
                (#{requestId}, #{accountId}, #{businessType}, #{businessId}, #{purpose},
                 #{title}, #{summary}, #{resultJson})
            ON DUPLICATE KEY UPDATE
                title = VALUES(title), summary = VALUES(summary),
                result_json = VALUES(result_json), update_time = NOW(3)
            """)
    int upsert(Map<String, Object> record);

    @Select("""
            <script>
            SELECT id, request_id AS requestId, business_type AS businessType,
                   business_id AS businessId, purpose, title, summary,
                   create_time AS createTime
            FROM ai_result_record
            WHERE account_id = #{accountId}
            <if test="businessType != null and businessType != ''">
                AND business_type = #{businessType}
            </if>
            ORDER BY id DESC
            LIMIT #{offset}, #{size}
            </script>
            """)
    List<Map<String, Object>> selectPage(@Param("accountId") Long accountId,
                                         @Param("businessType") String businessType,
                                         @Param("offset") int offset,
                                         @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM ai_result_record
            WHERE account_id = #{accountId}
            <if test="businessType != null and businessType != ''">
                AND business_type = #{businessType}
            </if>
            </script>
            """)
    long count(@Param("accountId") Long accountId,
               @Param("businessType") String businessType);

    @Select("""
            SELECT id, request_id AS requestId, business_type AS businessType,
                   business_id AS businessId, purpose, title, summary,
                   result_json AS resultJson, create_time AS createTime,
                   update_time AS updateTime
            FROM ai_result_record
            WHERE id = #{id} AND account_id = #{accountId}
            """)
    Map<String, Object> selectOwned(@Param("id") Long id,
                                    @Param("accountId") Long accountId);

    @Delete("DELETE FROM ai_result_record WHERE id = #{id} AND account_id = #{accountId}")
    int deleteOwned(@Param("id") Long id, @Param("accountId") Long accountId);
}
