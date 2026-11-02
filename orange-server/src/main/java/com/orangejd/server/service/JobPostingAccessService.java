package com.orangejd.server.service;

import com.orangejd.pojo.entity.JobPosting;
import com.orangejd.server.mapper.JobPostingMapper;
import com.orangejd.server.mapper.PlanHistoryMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JobPostingAccessService {
    private final JobPostingMapper jobs;
    private final PlanHistoryMapper plans;

    public JobPostingAccessService(JobPostingMapper jobs, PlanHistoryMapper plans) {
        this.jobs = jobs;
        this.plans = plans;
    }

    public int insert(JobPosting value) { return jobs.insert(value); }
    public JobPosting selectById(Long id) { return jobs.selectById(id); }
    public List<String> selectAllDirections() { return jobs.selectAllDirections(); }
    public List<JobPosting> selectApprovedByDirection(String direction) { return jobs.selectApprovedByDirection(direction); }
    public List<JobPosting> selectPending(int offset, int limit) { return jobs.selectPending(offset, limit); }
    public List<JobPosting> selectAll(String status, int offset, int limit) { return jobs.selectAll(status, offset, limit); }
    public int countAll(String status) { return jobs.countAll(status); }
    public int countByCompanyPosition(String company, String position) { return jobs.countByCompanyPosition(company, position); }
    public int deleteById(Long id) { return jobs.deleteById(id); }
    public int updateContent(Long id, String position, String direction, String content) {
        return jobs.updateContent(id, position, direction, content);
    }
    public int updatePhase6Owned(Long id, Long accountId, String phase6) {
        return plans.updatePhase6Owned(id, accountId, phase6);
    }
}
