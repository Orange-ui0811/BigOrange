package com.orangejd.server.dto;

import jakarta.validation.constraints.Size;

public record PersonaMaterialDecisionRequest(
        @Size(max = 500, message = "画像内容最多 500 个字符") String content) {
}
