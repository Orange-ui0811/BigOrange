package com.orangejd.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PersonaEntryRequest(
        @NotBlank(message = "请选择画像维度") String dimension,
        @NotBlank(message = "请输入画像内容")
        @Size(max = 500, message = "画像内容最多 500 个字符") String content) {
}
