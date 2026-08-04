package com.orangejd.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BeginnerProjectStatusRequest(
        @NotBlank String status,
        @Size(max = 500) String hiddenReason) {
}
