package com.orangejd.server.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface PersonaWorkshopMapper {

    @Select("""
            SELECT id, dimension_code AS dimensionCode, content, source_type AS sourceType,
                   source_material_id AS sourceMaterialId, position_no AS positionNo,
                   create_time AS createTime, update_time AS updateTime
            FROM persona_profile_entry
            WHERE account_id = #{accountId} AND status = 'ACTIVE'
            ORDER BY FIELD(dimension_code, 'GOAL','INTEREST','SKILL','RESOURCE','GROWTH','LEFT_HAND','RIGHT_HAND'),
                     position_no, id
            """)
    List<Map<String, Object>> selectEntries(Long accountId);

    @Select("""
            SELECT id, account_id AS accountId, dimension_code AS dimensionCode, content,
                   source_type AS sourceType, source_material_id AS sourceMaterialId, status
            FROM persona_profile_entry
            WHERE id = #{id} AND account_id = #{accountId}
            """)
    Map<String, Object> selectEntryOwned(@Param("id") Long id, @Param("accountId") Long accountId);

    @Select("""
            SELECT id, dimension_code AS dimensionCode, content, source_type AS sourceType,
                   source_material_id AS sourceMaterialId, create_time AS createTime
            FROM persona_profile_entry
            WHERE account_id = #{accountId} AND source_material_id = #{materialId}
            LIMIT 1
            """)
    Map<String, Object> selectEntryByMaterial(@Param("accountId") Long accountId,
                                               @Param("materialId") Long materialId);

    @Insert("""
            INSERT INTO persona_profile_entry
                (account_id, dimension_code, content, source_type, source_material_id, position_no)
            VALUES
                (#{accountId}, #{dimensionCode}, #{content}, #{sourceType}, #{sourceMaterialId}, #{positionNo})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertEntry(Map<String, Object> entry);

    @Update("""
            UPDATE persona_profile_entry
            SET dimension_code = #{dimensionCode}, content = #{content}, update_time = NOW(3)
            WHERE id = #{id} AND account_id = #{accountId} AND status = 'ACTIVE'
            """)
    int updateEntry(Map<String, Object> entry);

    @Update("""
            UPDATE persona_profile_entry
            SET status = 'ARCHIVED', update_time = NOW(3)
            WHERE id = #{id} AND account_id = #{accountId} AND status = 'ACTIVE'
            """)
    int archiveEntry(@Param("id") Long id, @Param("accountId") Long accountId);

    @Select("""
            <script>
            SELECT id, dimension_code AS dimensionCode, event_type AS eventType,
                   evidence_type AS evidenceType, evidence_ref AS evidenceRef,
                   evidence_summary AS evidenceSummary, suggested_content AS suggestedContent,
                   status, captured_at AS capturedAt, reviewed_at AS reviewedAt
            FROM persona_material
            WHERE account_id = #{accountId}
            <if test="status != null and status != ''">AND status = #{status}</if>
            ORDER BY captured_at DESC, id DESC
            LIMIT #{limit}
            </script>
            """)
    List<Map<String, Object>> selectMaterials(@Param("accountId") Long accountId,
                                               @Param("status") String status,
                                               @Param("limit") int limit);

    @Select("""
            SELECT id, account_id AS accountId, dimension_code AS dimensionCode,
                   event_type AS eventType, evidence_type AS evidenceType,
                   evidence_ref AS evidenceRef, evidence_summary AS evidenceSummary,
                   suggested_content AS suggestedContent, status
            FROM persona_material
            WHERE id = #{id} AND account_id = #{accountId}
            """)
    Map<String, Object> selectMaterialOwned(@Param("id") Long id, @Param("accountId") Long accountId);

    @Insert("""
            INSERT IGNORE INTO persona_material
                (account_id, dimension_code, event_type, evidence_type, evidence_ref,
                 evidence_summary, suggested_content, dedupe_key)
            VALUES
                (#{accountId}, #{dimensionCode}, #{eventType}, #{evidenceType}, #{evidenceRef},
                 #{evidenceSummary}, #{suggestedContent}, #{dedupeKey})
            """)
    int insertMaterial(Map<String, Object> material);

    @Update("""
            UPDATE persona_material
            SET status = 'ACCEPTED', reviewed_at = NOW(3), update_time = NOW(3)
            WHERE id = #{id} AND account_id = #{accountId} AND status = 'PENDING'
            """)
    int acceptMaterial(@Param("id") Long id, @Param("accountId") Long accountId);

    @Update("""
            UPDATE persona_material
            SET status = 'REJECTED', reviewed_at = NOW(3), update_time = NOW(3)
            WHERE id = #{id} AND account_id = #{accountId} AND status = 'PENDING'
            """)
    int rejectMaterial(@Param("id") Long id, @Param("accountId") Long accountId);

    @Select("""
            SELECT slot_code AS slotCode, item_code AS itemCode, variant_code AS variantCode,
                   color_hex AS colorHex, equipped, transform_json AS transformJson,
                   blueprint_json AS blueprintJson, blueprint_version AS blueprintVersion,
                   update_time AS updateTime
            FROM persona_equipment
            WHERE account_id = #{accountId}
            """)
    List<Map<String, Object>> selectEquipment(Long accountId);

    @Insert("""
            INSERT INTO persona_equipment
                (account_id, slot_code, item_code, variant_code, color_hex, equipped, transform_json)
            VALUES
                (#{accountId}, #{slotCode}, #{itemCode}, #{variantCode}, #{colorHex}, #{equipped}, #{transformJson})
            ON DUPLICATE KEY UPDATE
                item_code = VALUES(item_code), variant_code = VALUES(variant_code),
                color_hex = VALUES(color_hex), equipped = VALUES(equipped),
                transform_json = VALUES(transform_json), update_time = NOW(3)
            """)
    int upsertEquipment(Map<String, Object> equipment);

    @Insert("""
            INSERT INTO persona_equipment
                (account_id, slot_code, item_code, variant_code, color_hex, equipped,
                 transform_json, blueprint_json, blueprint_version)
            VALUES
                (#{accountId}, #{slotCode}, #{itemCode}, #{variantCode}, #{colorHex}, 0,
                 NULL, #{blueprintJson}, 1)
            ON DUPLICATE KEY UPDATE
                blueprint_json = VALUES(blueprint_json),
                blueprint_version = blueprint_version + 1,
                update_time = NOW(3)
            """)
    int upsertAccessoryBlueprint(Map<String, Object> blueprint);

    @Select("""
            SELECT showcase_enabled AS showcaseEnabled, avatar_enabled AS avatarEnabled,
                   body_color AS bodyColor, face_style AS faceStyle,
                   leaf_style AS leafStyle, surface_style AS surfaceStyle,
                   face_config AS faceConfig, hand_config AS handConfig,
                   update_time AS updateTime
            FROM persona_workshop_setting
            WHERE account_id = #{accountId}
            """)
    Map<String, Object> selectSettings(Long accountId);

    @Insert("""
            INSERT INTO persona_workshop_setting
                (account_id, showcase_enabled, avatar_enabled, body_color, face_style, leaf_style, surface_style, face_config, hand_config)
            VALUES
                (#{accountId}, #{showcaseEnabled}, #{avatarEnabled}, #{bodyColor}, #{faceStyle}, #{leafStyle}, #{surfaceStyle}, #{faceConfigJson}, #{handConfigJson})
            ON DUPLICATE KEY UPDATE
                showcase_enabled = VALUES(showcase_enabled),
                avatar_enabled = VALUES(avatar_enabled),
                body_color = VALUES(body_color), face_style = VALUES(face_style),
                leaf_style = VALUES(leaf_style), surface_style = VALUES(surface_style),
                face_config = VALUES(face_config), hand_config = VALUES(hand_config),
                update_time = NOW(3)
            """)
    int upsertSettings(Map<String, Object> settings);
}
