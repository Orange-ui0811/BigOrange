package com.orangejd.server.service;

import com.orangejd.pojo.entity.BeginnerProject;
import com.orangejd.pojo.entity.CareerProjectProfile;
import com.orangejd.server.mapper.BeginnerProjectMapper;
import com.orangejd.server.mapper.CareerProjectMapper;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Locale;

@Service
public class ProjectIdentityResolver {
    private final BeginnerProjectMapper beginnerProjects;
    private final CareerProjectMapper careerProjects;

    public ProjectIdentityResolver(BeginnerProjectMapper beginnerProjects, CareerProjectMapper careerProjects) {
        this.beginnerProjects = beginnerProjects;
        this.careerProjects = careerProjects;
    }

    public void enrich(Map<String, Object> metadata, String source, String sourceRef, String title) {
        if (!"GITHUB".equals(source)) return;
        String repository = normalize(sourceRef);
        if (repository.isBlank()) repository = normalize(title);
        if (repository.isBlank()) return;
        metadata.put("externalRepo", repository);
        CareerProjectProfile career = careerProjects.selectByFullName(repository);
        if (career != null && career.getProjectId() != null) {
            metadata.put("projectId", career.getProjectId());
            metadata.put("projectType", "CAREER_PROJECT");
            metadata.put("mappingStatus", "AUTO_MATCHED");
            metadata.put("mappingMethod", "FULL_NAME");
            return;
        }
        BeginnerProject beginner = beginnerProjects.selectByFullName(repository);
        if (beginner != null && beginner.getId() != null) {
            metadata.put("projectId", beginner.getId());
            metadata.put("projectType", "BEGINNER_PROJECT");
            metadata.put("mappingStatus", "AUTO_MATCHED");
            metadata.put("mappingMethod", "FULL_NAME");
            return;
        }
        metadata.put("mappingStatus", "UNMAPPED");
        metadata.put("mappingMethod", "FULL_NAME");
    }

    static String normalize(String value) {
        if (value == null) return "";
        String result = value.trim();
        int period = result.indexOf('|');
        if (period >= 0) result = result.substring(0, period);
        int github = result.indexOf("github.com/");
        if (github >= 0) result = result.substring(github + "github.com/".length());
        result = result.replaceFirst("^/+", "").replaceFirst("/+$", "");
        result = result.replaceFirst("\\.git$", "");
        int branch = result.indexOf("/tree/");
        if (branch >= 0) result = result.substring(0, branch);
        return result.toLowerCase(Locale.ROOT);
    }
}
