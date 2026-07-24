package com.orangejd.server.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface AgentDeviceMapper {

    @Insert("""
            INSERT INTO agent_device_link_code
                (account_id, code_hash, device_name, expires_at)
            VALUES (#{accountId}, #{codeHash}, #{deviceName}, #{expiresAt})
            """)
    int insertLinkCode(Map<String, Object> record);

    @Select("""
            SELECT id, account_id AS accountId, device_name AS deviceName, expires_at AS expiresAt
            FROM agent_device_link_code
            WHERE code_hash = #{codeHash} AND consumed_at IS NULL AND expires_at > NOW()
            """)
    Map<String, Object> selectUsableLinkCode(String codeHash);

    @Update("""
            UPDATE agent_device_link_code SET consumed_at = NOW()
            WHERE id = #{id} AND consumed_at IS NULL AND expires_at > NOW()
            """)
    int consumeLinkCode(Long id);

    @Insert("""
            INSERT INTO agent_device_credential
                (account_id, device_name, token_hash, token_hint, scopes, expires_at)
            VALUES
                (#{accountId}, #{deviceName}, #{tokenHash}, #{tokenHint}, #{scopes}, #{expiresAt})
            """)
    @org.apache.ibatis.annotations.Options(useGeneratedKeys = true, keyProperty = "id")
    int insertCredential(Map<String, Object> record);

    @Select("""
            SELECT id, account_id AS accountId, device_name AS deviceName, scopes, status,
                   expires_at AS expiresAt
            FROM agent_device_credential
            WHERE token_hash = #{tokenHash} AND status = 'ACTIVE' AND expires_at > NOW()
            """)
    Map<String, Object> selectActiveCredential(String tokenHash);

    @Update("UPDATE agent_device_credential SET last_used_at = NOW() WHERE id = #{id}")
    int touchCredential(Long id);

    @Select("""
            SELECT id, device_name AS deviceName, token_hint AS tokenHint, scopes, status,
                   last_used_at AS lastUsedAt, expires_at AS expiresAt,
                   create_time AS createTime, update_time AS updateTime
            FROM agent_device_credential
            WHERE account_id = #{accountId}
            ORDER BY create_time DESC
            """)
    List<Map<String, Object>> selectCredentialsByAccount(Long accountId);

    @Update("""
            UPDATE agent_device_credential SET status = 'REVOKED'
            WHERE id = #{id} AND account_id = #{accountId} AND status = 'ACTIVE'
            """)
    int revokeCredential(@Param("id") Long id, @Param("accountId") Long accountId);
}
