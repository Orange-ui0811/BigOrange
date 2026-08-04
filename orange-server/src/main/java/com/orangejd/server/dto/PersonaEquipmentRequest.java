package com.orangejd.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

public record PersonaEquipmentRequest(
        @NotBlank(message = "请选择装备") String itemCode,
        @NotBlank(message = "请选择装备款式") String variantCode,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "装备颜色格式无效") String colorHex,
        boolean equipped,
        Map<String, Double> transform) {

    public PersonaEquipmentRequest(String itemCode, String variantCode, String colorHex, boolean equipped) {
        this(itemCode, variantCode, colorHex, equipped, null);
    }
}
