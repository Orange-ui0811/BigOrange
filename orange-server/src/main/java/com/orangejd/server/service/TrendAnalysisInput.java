package com.orangejd.server.service;

import java.util.List;

record TrendAnalysisInput(List<String> topics, String purpose, String experience, List<String> languages,
                          String timeCommitment, String customTopics, String customPurpose, String customExp,
                          String customLang, String customTime, Boolean useRag,
                          Boolean preferBeginnerProjects, Boolean preferCareerProjects) {
}
