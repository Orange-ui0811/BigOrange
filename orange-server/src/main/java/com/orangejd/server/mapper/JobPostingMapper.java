package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.JobPosting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface JobPostingMapper {
    int insert(JobPosting record);
    JobPosting selectById(@Param("id") Long id);
    JobPosting selectByIdForUpdate(@Param("id") Long id);
    JobPosting selectByCompanyPosition(@Param("company") String company, @Param("position") String position);
    List<String> selectAllDirections();
    List<JobPosting> selectApprovedByDirection(@Param("direction") String direction);
    List<JobPosting> selectPending(@Param("offset") int offset, @Param("limit") int limit);
    List<JobPosting> selectAll(@Param("status") String status, @Param("offset") int offset, @Param("limit") int limit);
    int countAll(@Param("status") String status);
    int countPending();
    int updateAuditStatus(@Param("id") Long id, @Param("status") String status, @Param("reason") String reason);
    int deleteById(@Param("id") Long id);
    int countByCompanyPosition(@Param("company") String company, @Param("position") String position);
    int updateContent(@Param("id") Long id, @Param("position") String position,
                      @Param("direction") String direction, @Param("displayContent") String displayContent);
}
