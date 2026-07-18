package com.orangejd.server.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface StorageCleanupMapper {
    @Insert("""
            INSERT INTO storage_cleanup_job (object_key, attempts, next_retry_time, last_error)
            VALUES (#{objectKey}, 1, DATE_ADD(NOW(), INTERVAL 5 MINUTE), #{error})
            ON DUPLICATE KEY UPDATE attempts = attempts + 1,
                next_retry_time = DATE_ADD(NOW(), INTERVAL 10 MINUTE),
                last_error = VALUES(last_error)
            """)
    int enqueue(@Param("objectKey") String objectKey, @Param("error") String error);

    @Select("""
            SELECT object_key FROM storage_cleanup_job
            WHERE next_retry_time <= NOW() AND attempts < 20
            ORDER BY next_retry_time ASC LIMIT #{limit}
            """)
    List<String> selectDue(@Param("limit") int limit);

    @Delete("DELETE FROM storage_cleanup_job WHERE object_key = #{objectKey}")
    int delete(@Param("objectKey") String objectKey);

    @Update("""
            UPDATE storage_cleanup_job SET attempts = attempts + 1,
                next_retry_time = DATE_ADD(NOW(), INTERVAL 10 MINUTE),
                last_error = #{error}
            WHERE object_key = #{objectKey}
            """)
    int recordFailure(@Param("objectKey") String objectKey, @Param("error") String error);
}
