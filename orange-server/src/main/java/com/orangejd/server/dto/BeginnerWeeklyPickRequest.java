package com.orangejd.server.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BeginnerWeeklyPickRequest(
        @NotNull(message = "请选择本周精选项目")
        @Size(min = 3, max = 3, message = "本周精选必须恰好选择 3 个项目")
        List<@NotNull(message = "项目编号不能为空") Long> projectIds) {
}
