package com.orangejd.server.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface LaunchAssessmentMapper {

    @Insert("""
            INSERT INTO launch_assessment
                (account_id, project_name, repository_url, project_summary, target_audience,
                 budget, region, expected_users, upload_types, payment_required)
            VALUES
                (#{accountId}, #{projectName}, #{repositoryUrl}, #{projectSummary}, #{targetAudience},
                 #{budget}, #{region}, #{expectedUsers}, #{uploadTypes}, #{paymentRequired})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Map<String, Object> assessment);

    @Select("""
            SELECT id, account_id AS accountId, project_name AS projectName,
                   repository_url AS repositoryUrl, project_summary AS projectSummary,
                   target_audience AS targetAudience, budget, region,
                   expected_users AS expectedUsers, upload_types AS uploadTypes,
                   payment_required AS paymentRequired, current_stage AS currentStage,
                   status, create_time AS createTime, update_time AS updateTime
            FROM launch_assessment
            WHERE id = #{id} AND account_id = #{accountId}
            """)
    Map<String, Object> selectOwned(@Param("id") Long id, @Param("accountId") Long accountId);

    @Select("""
            SELECT id, project_name AS projectName, repository_url AS repositoryUrl,
                   target_audience AS targetAudience, current_stage AS currentStage,
                   status, create_time AS createTime, update_time AS updateTime
            FROM launch_assessment
            WHERE account_id = #{accountId}
            ORDER BY update_time DESC, id DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> selectByAccountId(@Param("accountId") Long accountId,
                                                 @Param("limit") int limit);

    @Select("""
            SELECT stage_no AS stageNo, stage_title AS stageTitle, content,
                   segment_cost AS segmentCost, create_time AS createTime, update_time AS updateTime
            FROM launch_assessment_stage
            WHERE assessment_id = #{assessmentId}
            ORDER BY stage_no
            """)
    List<Map<String, Object>> selectStages(Long assessmentId);

    @Select("""
            SELECT stage_no AS stageNo, stage_title AS stageTitle, content,
                   segment_cost AS segmentCost, create_time AS createTime, update_time AS updateTime
            FROM launch_assessment_stage
            WHERE assessment_id = #{assessmentId} AND stage_no = #{stageNo}
            """)
    Map<String, Object> selectStage(@Param("assessmentId") Long assessmentId,
                                    @Param("stageNo") int stageNo);

    @Insert("""
            INSERT INTO launch_assessment_stage
                (assessment_id, stage_no, stage_title, content, segment_cost)
            VALUES (#{assessmentId}, #{stageNo}, #{stageTitle}, #{content}, #{segmentCost})
            ON DUPLICATE KEY UPDATE stage_title = VALUES(stage_title), content = VALUES(content),
                                    segment_cost = VALUES(segment_cost), update_time = NOW()
            """)
    int upsertStage(Map<String, Object> stage);

    @Delete("DELETE FROM launch_assessment_stage WHERE assessment_id = #{assessmentId} AND stage_no > #{stageNo}")
    int deleteStagesAfter(@Param("assessmentId") Long assessmentId, @Param("stageNo") int stageNo);

    @Update("""
            UPDATE launch_assessment
            SET current_stage = #{currentStage},
                status = CASE WHEN #{currentStage} >= 8 THEN 'COMPLETED' ELSE 'IN_PROGRESS' END,
                update_time = NOW()
            WHERE id = #{id} AND account_id = #{accountId}
            """)
    int updateProgress(@Param("id") Long id, @Param("accountId") Long accountId,
                       @Param("currentStage") int currentStage);

    @Delete("DELETE FROM launch_assessment WHERE id = #{id} AND account_id = #{accountId}")
    int deleteOwned(@Param("id") Long id, @Param("accountId") Long accountId);

    @Select("""
            SELECT la.id, la.account_id AS accountId, a.username,
                   COALESCE(NULLIF(up.nickname, ''), a.username) AS accountName,
                   la.project_name AS projectName, la.repository_url AS repositoryUrl,
                   la.current_stage AS currentStage, la.status,
                   COALESCE(SUM(las.segment_cost), 0) AS totalCost,
                   la.create_time AS createTime, la.update_time AS updateTime
            FROM launch_assessment la
            JOIN account a ON a.id = la.account_id
            LEFT JOIN user_profile up ON up.account_id = a.id
            LEFT JOIN launch_assessment_stage las ON las.assessment_id = la.id
            GROUP BY la.id, la.account_id, a.username, up.nickname, la.project_name,
                     la.repository_url, la.current_stage, la.status, la.create_time, la.update_time
            ORDER BY la.update_time DESC, la.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> selectAdminPage(@Param("offset") int offset, @Param("limit") int limit);

    @Select("""
            SELECT COUNT(DISTINCT la.id) AS total,
                   COUNT(DISTINCT CASE WHEN la.status = 'COMPLETED' THEN la.id END) AS completed,
                   COALESCE(SUM(las.segment_cost), 0) AS totalCost
            FROM launch_assessment la
            LEFT JOIN launch_assessment_stage las ON las.assessment_id = la.id
            """)
    Map<String, Object> selectAdminStats();

    @Select("""
            SELECT la.id, la.account_id AS accountId, a.username,
                   COALESCE(NULLIF(up.nickname, ''), a.username) AS accountName,
                   la.project_name AS projectName, la.repository_url AS repositoryUrl,
                   la.project_summary AS projectSummary, la.target_audience AS targetAudience,
                   la.budget, la.region, la.expected_users AS expectedUsers,
                   la.upload_types AS uploadTypes, la.payment_required AS paymentRequired,
                   la.current_stage AS currentStage, la.status,
                   la.create_time AS createTime, la.update_time AS updateTime
            FROM launch_assessment la
            JOIN account a ON a.id = la.account_id
            LEFT JOIN user_profile up ON up.account_id = a.id
            WHERE la.id = #{id}
            """)
    Map<String, Object> selectAdminDetail(Long id);
}
