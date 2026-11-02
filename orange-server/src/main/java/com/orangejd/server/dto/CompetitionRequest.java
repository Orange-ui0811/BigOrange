package com.orangejd.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CompetitionRequest(
        @NotBlank(message = "请输入比赛名称")
        @Size(max = 200, message = "比赛名称不能超过200字")
        String name,
        @NotBlank(message = "请输入比赛官网")
        @Size(max = 768, message = "比赛网址过长")
        String sourceUrl,
        @NotBlank(message = "请输入比赛简介")
        @Size(max = 1200, message = "比赛简介不能超过1200字")
        String summary,
        @NotBlank(message = "请输入举办方")
        @Size(max = 200, message = "举办方不能超过200字")
        String organizer,
        LocalDateTime startTime,
        LocalDateTime endTime
) {}
