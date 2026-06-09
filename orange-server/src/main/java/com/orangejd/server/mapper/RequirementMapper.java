package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.Requirement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface RequirementMapper {

    int insert(Requirement requirement);

    int update(Requirement requirement);

    Requirement selectById(@Param("id") Long id);

    List<Requirement> selectByPublisherId(@Param("publisherId") Long publisherId, @Param("offset") int offset, @Param("limit") int limit);

    int countByPublisherId(@Param("publisherId") Long publisherId);

    int deleteById(@Param("id") Long id);

    int updateAuditStatus(@Param("id") Long id, @Param("auditStatus") String auditStatus);

    int updateVisibility(@Param("id") Long id, @Param("visibilityStatus") String visibilityStatus);

    /** 市场查询 */
    List<Requirement> selectPublic(@Param("categoryId") Long categoryId,
                                   @Param("budgetMin") BigDecimal budgetMin,
                                   @Param("budgetMax") BigDecimal budgetMax,
                                   @Param("keyword") String keyword,
                                   @Param("offset") int offset, @Param("limit") int limit);

    int countPublic(@Param("categoryId") Long categoryId,
                    @Param("budgetMin") BigDecimal budgetMin,
                    @Param("budgetMax") BigDecimal budgetMax,
                    @Param("keyword") String keyword);

    /** 管理端查询 */
    List<Requirement> selectAll(@Param("auditStatus") String auditStatus, @Param("offset") int offset, @Param("limit") int limit);

    int countAll(@Param("auditStatus") String auditStatus);
}
