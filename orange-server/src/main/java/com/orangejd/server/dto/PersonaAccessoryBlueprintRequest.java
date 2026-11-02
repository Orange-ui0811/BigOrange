package com.orangejd.server.dto;

import java.util.List;
import java.util.Map;

public record PersonaAccessoryBlueprintRequest(
        String name,
        List<Part> parts) {

    public record Part(
            String id,
            String label,
            String shape,
            Boolean visible,
            Boolean locked,
            Map<String, Double> parameters,
            Map<String, Double> transform,
            Material material) {
    }

    public record Material(
            String color,
            Double opacity,
            Double metalness,
            Double roughness,
            Double emissiveIntensity) {
    }
}
