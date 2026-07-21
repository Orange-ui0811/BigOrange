package com.orangejd.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface OpenSourceMapper {
    List<Map<String, Object>> selectEnabledRepositories();
    List<Map<String, Object>> selectRepositories();
    Map<String, Object> selectRepositoryById(Long id);
    Map<String, Object> selectRepositoryByFullName(String fullName);
    int insertRepository(Map<String, Object> repository);
    int updateRepository(Map<String, Object> repository);
    int updateRepositoryMetadata(Map<String, Object> repository);
    int updateRepositorySync(@Param("id") Long id, @Param("status") String status,
                             @Param("error") String error);

    int upsertIssue(Map<String, Object> issue);
    Map<String, Object> selectIssueById(Long id);
    List<Map<String, Object>> selectIssuesByIds(@Param("ids") List<Long> ids);
    List<Map<String, Object>> selectPublicIssues(@Param("offset") int offset,
                                                 @Param("limit") int limit,
                                                 @Param("difficulty") String difficulty,
                                                 @Param("participationStatus") String participationStatus,
                                                 @Param("tech") String tech,
                                                 @Param("keyword") String keyword,
                                                 @Param("updatedDays") Integer updatedDays);
    long countPublicIssues(@Param("difficulty") String difficulty,
                           @Param("participationStatus") String participationStatus,
                           @Param("tech") String tech,
                           @Param("keyword") String keyword,
                           @Param("updatedDays") Integer updatedDays);
    List<Map<String, Object>> selectTickerIssues(int limit);
    List<Map<String, Object>> selectPublishedForVerification(int limit);
    List<Map<String, Object>> selectTranslationPending(int limit);
    int updateIssueTranslation(@Param("id") Long id, @Param("titleZh") String titleZh,
                               @Param("summaryZh") String summaryZh,
                               @Param("status") String status);
    int updateIssueVerification(Map<String, Object> issue);

    List<Map<String, Object>> selectAdminIssues(@Param("offset") int offset,
                                                @Param("limit") int limit,
                                                @Param("keyword") String keyword,
                                                @Param("published") Integer published,
                                                @Param("translationStatus") String translationStatus);
    long countAdminIssues(@Param("keyword") String keyword,
                          @Param("published") Integer published,
                          @Param("translationStatus") String translationStatus);
    Map<String, Object> selectAdminStats();
    int updateIssueAdmin(Map<String, Object> issue);

    int upsertGuide(Map<String, Object> guide);
    List<Map<String, Object>> selectGuidesByAccount(Long accountId);
    Map<String, Object> selectGuideOwned(@Param("id") Long id, @Param("accountId") Long accountId);
}
