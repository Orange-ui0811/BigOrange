package com.orangejd.server.dto;

import jakarta.validation.constraints.NotBlank;

public record CareerJobAssessmentRequest(@NotBlank String roleCode, Long jobId) {
}
