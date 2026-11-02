package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.CareerProjectProfile;
import com.orangejd.pojo.entity.CareerRoleScore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface CareerProjectMapper {
    int upsertProfile(CareerProjectProfile profile);
    int deleteRoleScores(@Param("projectId") Long projectId);
    int insertRoleScore(CareerRoleScore score);
    CareerProjectProfile selectById(@Param("projectId") Long projectId);
    CareerProjectProfile selectByFullName(@Param("fullName") String fullName);
    List<CareerRoleScore> selectRoleScores(@Param("projectId") Long projectId);
    List<CareerProjectProfile> selectPublic(@Param("keyword") String keyword,
                                            @Param("language") String language,
                                            @Param("difficulty") String difficulty,
                                            @Param("roleCode") String roleCode,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);
    long countPublic(@Param("keyword") String keyword,
                     @Param("language") String language,
                     @Param("difficulty") String difficulty,
                     @Param("roleCode") String roleCode);
    List<CareerProjectProfile> selectAdmin(@Param("keyword") String keyword,
                                           @Param("status") String status,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);
    long countAdmin(@Param("keyword") String keyword, @Param("status") String status);
    List<CareerProjectProfile> selectPendingEnrichment(@Param("limit") int limit);
    int updateEnrichment(@Param("projectId") Long projectId,
                         @Param("summaryZh") String summaryZh,
                         @Param("careerValue") String careerValue,
                         @Param("proofPoints") String proofPoints,
                         @Param("interviewTopics") String interviewTopics,
                         @Param("modificationPlan") String modificationPlan,
                         @Param("mainRisks") String mainRisks,
                         @Param("estimatedDuration") String estimatedDuration,
                         @Param("status") String status);
    int updateStatus(@Param("projectId") Long projectId,
                     @Param("status") String status,
                     @Param("hiddenReason") String hiddenReason);
    int deleteWeeklyPicks(@Param("weekStart") LocalDate weekStart);
    int insertWeeklyPick(@Param("weekStart") LocalDate weekStart,
                         @Param("projectId") Long projectId,
                         @Param("rankNo") int rankNo,
                         @Param("selectionSource") String selectionSource);
    List<CareerProjectProfile> selectWeeklyPicks(@Param("weekStart") LocalDate weekStart);
    String selectWeeklyPickSource(@Param("weekStart") LocalDate weekStart);
    List<CareerProjectProfile> selectTopForWeekly(@Param("limit") int limit);
    List<CareerProjectProfile> selectAllPublished();
}
