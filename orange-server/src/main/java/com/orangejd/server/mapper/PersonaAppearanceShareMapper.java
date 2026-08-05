package com.orangejd.server.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Map;

@Mapper
public interface PersonaAppearanceShareMapper {

    @Select("""
            SELECT code, schema_version AS schemaVersion, payload_hash AS payloadHash,
                   create_time AS createTime
            FROM persona_appearance_share
            WHERE owner_account_id = #{accountId} AND status = 'ACTIVE'
            ORDER BY id DESC LIMIT 1
            """)
    Map<String, Object> selectActiveByOwner(Long accountId);

    @Select("""
            SELECT code, schema_version AS schemaVersion, payload_json AS payloadJson,
                   status, create_time AS createTime
            FROM persona_appearance_share
            WHERE code = #{code} AND status = 'ACTIVE'
            LIMIT 1
            """)
    Map<String, Object> selectActiveByCode(String code);

    @Update("""
            UPDATE persona_appearance_share
            SET status = 'REVOKED', update_time = NOW(3)
            WHERE owner_account_id = #{accountId} AND status = 'ACTIVE'
            """)
    int revokeActiveByOwner(Long accountId);

    @Insert("""
            INSERT IGNORE INTO persona_appearance_share
                (code, owner_account_id, schema_version, payload_json, payload_hash)
            VALUES
                (#{code}, #{ownerAccountId}, #{schemaVersion}, #{payloadJson}, #{payloadHash})
            """)
    int insert(Map<String, Object> share);

    @Update("""
            UPDATE persona_appearance_share
            SET import_count = import_count + 1, last_imported_at = NOW(3), update_time = NOW(3)
            WHERE code = #{code} AND status = 'ACTIVE'
            """)
    int recordImport(@Param("code") String code);
}
