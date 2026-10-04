package com.orangejd.server.customer;

import com.orangejd.pojo.entity.*;
import org.apache.ibatis.annotations.*;
import java.util.*;

/** Read-only recommendation inventory; publication is a label, not an admission requirement. */
@Mapper
public interface CustomerCatalogMapper {
    @Select("SELECT source_ref FROM rag_b_document WHERE source_type IN ('BEGINNER_PROJECT','CAREER_PROJECT','GITHUB') AND status='READY' AND deleted_at IS NULL AND active_version_id IS NOT NULL")
    List<String> evidenceSources();
    @Select("SELECT COUNT(*) FROM career_project_profile cp JOIN beginner_project bp ON bp.id=cp.project_id WHERE cp.project_id=#{id} AND cp.status IN ('PUBLISHED','CANDIDATE') AND bp.status NOT IN ('HIDDEN','INVALID') AND COALESCE(bp.archived,0)=0")
    int eligibleCareer(long id);
    @Select("SELECT * FROM beginner_project WHERE status IN ('PUBLISHED','CANDIDATE') AND COALESCE(archived,0)=0 ORDER BY id")
    List<BeginnerProject> beginners();
    @Select("""
        SELECT bp.id AS project_id,bp.full_name,bp.html_url,bp.primary_language,bp.tech_stack,bp.archived,
               bp.requires_docker,bp.requires_database,bp.requires_secrets,cp.difficulty,cp.estimated_duration,
               cp.status,cp.summary_zh,cp.overall_score,cp.career_value,cp.proof_points,cp.interview_topics,cp.modification_plan,cp.main_risks
        FROM career_project_profile cp JOIN beginner_project bp ON bp.id=cp.project_id
        WHERE cp.status IN ('PUBLISHED','CANDIDATE') AND bp.status NOT IN ('HIDDEN','INVALID')
          AND COALESCE(bp.archived,0)=0 ORDER BY cp.project_id
        """)
    List<CareerProjectProfile> careers();
    @Select("""
        SELECT d.id,d.source_ref,d.title,d.active_version_id,LEFT(v.original_content,12000) AS topic_text
        FROM rag_b_document d JOIN rag_b_document_version v ON v.id=d.active_version_id AND v.document_id=d.id
        WHERE d.source_type='GITHUB' AND d.status='READY' AND d.deleted_at IS NULL AND d.active_version_id IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM beginner_project bp WHERE LOWER(bp.full_name)=LOWER(SUBSTRING_INDEX(d.source_ref,'|',1))
              AND (bp.status IN ('HIDDEN','INVALID') OR COALESCE(bp.archived,0)=1))
          AND NOT EXISTS (SELECT 1 FROM career_project_profile cp JOIN beginner_project bp ON bp.id=cp.project_id
              WHERE LOWER(bp.full_name)=LOWER(SUBSTRING_INDEX(d.source_ref,'|',1)) AND cp.status IN ('HIDDEN','INVALID'))
        ORDER BY d.update_time DESC,d.id DESC
        """)
    List<Map<String,Object>> externalDocuments();
    @Select("""
        SELECT d.id,d.source_ref,d.title,d.active_version_id,LEFT(v.original_content,12000) AS topic_text
        FROM rag_b_document d JOIN rag_b_document_version v ON v.id=d.active_version_id AND v.document_id=d.id
        WHERE d.id=#{id} AND d.source_type='GITHUB' AND d.status='READY'
          AND d.deleted_at IS NULL AND d.active_version_id IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM beginner_project bp WHERE LOWER(bp.full_name)=LOWER(SUBSTRING_INDEX(d.source_ref,'|',1))
              AND (bp.status IN ('HIDDEN','INVALID') OR COALESCE(bp.archived,0)=1))
          AND NOT EXISTS (SELECT 1 FROM career_project_profile cp JOIN beginner_project bp ON bp.id=cp.project_id
              WHERE LOWER(bp.full_name)=LOWER(SUBSTRING_INDEX(d.source_ref,'|',1)) AND cp.status IN ('HIDDEN','INVALID'))
        """)
    Map<String,Object> externalDocument(long id);
}
