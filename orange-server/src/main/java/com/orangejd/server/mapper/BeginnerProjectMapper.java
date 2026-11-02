package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.BeginnerProject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface BeginnerProjectMapper {
    int upsertMetadata(BeginnerProject project);
    BeginnerProject selectById(@Param("id") Long id);
    BeginnerProject selectByFullName(@Param("fullName") String fullName);
    List<BeginnerProject> selectPublic(@Param("keyword") String keyword,
                                       @Param("language") String language,
                                       @Param("difficulty") String difficulty,
                                       @Param("topic") String topic,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);
    long countPublic(@Param("keyword") String keyword,
                     @Param("language") String language,
                     @Param("difficulty") String difficulty,
                     @Param("topic") String topic);
    List<BeginnerProject> selectAdmin(@Param("keyword") String keyword,
                                      @Param("status") String status,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);
    long countAdmin(@Param("keyword") String keyword, @Param("status") String status);
    List<BeginnerProject> selectPendingEnrichment(@Param("limit") int limit);
    List<BeginnerProject> selectDueVerification(@Param("before") LocalDateTime before,
                                                @Param("limit") int limit);
    List<BeginnerProject> selectRecommended(@Param("topics") List<String> topics,
                                            @Param("languages") List<String> languages,
                                            @Param("limit") int limit);
    int updateEnrichment(@Param("id") Long id,
                         @Param("descriptionZh") String descriptionZh,
                         @Param("whatIsIt") String whatIsIt,
                         @Param("beginnerReason") String beginnerReason,
                         @Param("modificationIdeas") String modificationIdeas,
                         @Param("mainRisks") String mainRisks,
                         @Param("finalRecommendation") String finalRecommendation,
                         @Param("estimatedDuration") String estimatedDuration,
                         @Param("vibeCodingFit") int vibeCodingFit,
                         @Param("showcaseValue") int showcaseValue,
                         @Param("extensionPotential") int extensionPotential,
                         @Param("scopeClarity") int scopeClarity,
                         @Param("contentPotential") int contentPotential,
                         @Param("editorialScore") int editorialScore,
                         @Param("status") String status);
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("hiddenReason") String hiddenReason);
    int deleteEvidence(@Param("projectId") Long projectId, @Param("scoreVersion") String scoreVersion);
    int insertEvidence(@Param("projectId") Long projectId,
                       @Param("runId") Long runId,
                       @Param("scoreVersion") String scoreVersion,
                       @Param("dimensionKey") String dimensionKey,
                       @Param("score") int score,
                       @Param("maxScore") int maxScore,
                       @Param("evidenceText") String evidenceText);
    List<Map<String, Object>> selectEvidence(@Param("projectId") Long projectId);
    int deleteWeeklyPicks(@Param("weekStart") LocalDate weekStart);
    int insertWeeklyPick(@Param("weekStart") LocalDate weekStart,
                         @Param("projectId") Long projectId,
                         @Param("rankNo") int rankNo,
                         @Param("selectionSource") String selectionSource);
    List<BeginnerProject> selectWeeklyPicks(@Param("weekStart") LocalDate weekStart);
    String selectWeeklyPickSource(@Param("weekStart") LocalDate weekStart);
    List<BeginnerProject> selectTopForWeekly(@Param("limit") int limit);
    List<BeginnerProject> selectAllPublished();
}
