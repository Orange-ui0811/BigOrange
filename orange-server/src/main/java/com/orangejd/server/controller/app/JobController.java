package com.orangejd.server.controller.app;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.entity.JobPosting;
import com.orangejd.server.mapper.JobPostingMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/app/job")
public class JobController {

    @Autowired private JobPostingMapper mapper;
    @Autowired private com.orangejd.server.service.JobMatchService jobMatchService;

    @PostMapping("/match")
    public Result<?> match(@RequestBody Map<String, String> body) {
        String planContent = body.get("planContent");
        if (planContent == null || planContent.isEmpty()) return Result.error("请输入方案内容");
        try {
            return Result.success(jobMatchService.match(planContent));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/directions")
    public Result<List<String>> directions() {
        return Result.success(mapper.selectAllDirections());
    }

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(@RequestParam String direction) {
        List<JobPosting> jobs = mapper.selectApprovedByDirection(direction);
        List<Map<String, Object>> result = new ArrayList<>();
        for (JobPosting j : jobs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", j.getId());
            m.put("position", j.getPosition());
            m.put("direction", j.getDirection());
            m.put("createTime", j.getCreateTime() != null ? j.getCreateTime().toString() : "");
            result.add(m);
        }
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        JobPosting j = mapper.selectById(id);
        if (j == null || !"APPROVED".equals(j.getAuditStatus())) {
            return Result.error("岗位不存在");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", j.getId());
        m.put("position", j.getPosition());
        m.put("direction", j.getDirection());
        m.put("displayContent", j.getDisplayContent());
        m.put("createTime", j.getCreateTime() != null ? j.getCreateTime().toString() : "");
        // 不返回 company
        return Result.success(m);
    }

    @PostMapping("/submit")
    public Result<?> submit(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long accountId = BaseContext.getCurrentId();
        String position = body.get("position");
        String direction = body.get("direction");
        String content = body.get("content");

        if (position == null || position.isEmpty()) return Result.error("请输入岗位名称");
        if (content == null || content.isEmpty()) return Result.error("请输入JD内容");

        // 查重
        if (mapper.countByCompanyPosition("USER_SUBMIT", position) > 0)
            return Result.error("该岗位可能已存在，请检查");

        // 清洗公司名
        String cleanedContent = stripCompanyNames(content);

        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) ip = request.getRemoteAddr();

        JobPosting jp = new JobPosting();
        jp.setCompany("USER_SUBMIT");
        jp.setPosition(position.replaceAll("(?i)(拓竹|字节跳动|腾讯|阿里巴巴|百度|美团|华为|网易|米哈游|小米|DJI)", ""));
        jp.setDirection(direction != null ? direction : "");
        jp.setContent(content);
        jp.setDisplayContent(cleanedContent);
        jp.setAuditStatus("PENDING");
        jp.setSubmitterId(accountId);
        jp.setSubmitterIp(ip);
        jp.setSubmitterRole("USER");
        mapper.insert(jp);
        return Result.success(Map.of("id", jp.getId()));
    }

    private String stripCompanyNames(String text) {
        String[] companies = {"拓竹", "字节跳动", "腾讯", "阿里巴巴", "百度", "美团", "华为", "网易", "米哈游", "小米", "DJI"};
        for (String c : companies) text = text.replace(c, "该公司");
        return text;
    }
}
