package com.orangejd.server.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class BeginnerEditorialPolicy {
    public static final int PUBLIC_SCORE = 76;

    public int score(Ratings ratings) {
        return (ratings.vibeCodingFit() * 30
                + ratings.showcaseValue() * 25
                + ratings.extensionPotential() * 15
                + ratings.scopeClarity() * 15
                + ratings.contentPotential() * 15) / 5;
    }

    public boolean publishable(int technicalScore, Ratings ratings) {
        return technicalScore >= 65
                && score(ratings) >= PUBLIC_SCORE
                && ratings.vibeCodingFit() >= 4
                && ratings.showcaseValue() >= 4
                && ratings.extensionPotential() >= 4
                && ratings.scopeClarity() >= 4
                && ratings.contentPotential() >= 4;
    }

    public Ratings calibrate(Ratings raw, Signals signals) {
        int vibe = bounded(raw.vibeCodingFit());
        int showcase = bounded(raw.showcaseValue());
        int extension = bounded(raw.extensionPotential());
        int scope = bounded(raw.scopeClarity());
        int content = bounded(raw.contentPotential());

        int heavyDependencies = countTrue(signals.requiresDocker(), signals.requiresDatabase(),
                signals.requiresSecrets(), signals.requiresExternalService(), signals.requiresGpu(),
                signals.requiresNativeBuild());
        if (signals.technicalScore() < 75 || heavyDependencies >= 2) vibe = Math.min(vibe, 3);
        else if (heavyDependencies == 1) vibe = Math.min(vibe, 4);

        if (!signals.hasQuickStart()) {
            showcase = Math.min(showcase, 3);
            scope = Math.min(scope, 3);
        }
        if ("LARGE".equalsIgnoreCase(signals.codebaseSize())) {
            scope = Math.min(scope, 2);
            extension = Math.min(extension, 3);
        } else if ("MEDIUM".equalsIgnoreCase(signals.codebaseSize())) {
            scope = Math.min(scope, 4);
        }
        if (signals.requiresNativeBuild()) extension = Math.min(extension, 3);

        String evidence = signals.searchableText() == null
                ? "" : signals.searchableText().toLowerCase(Locale.ROOT);
        boolean visualOutcome = containsAny(evidence, "visual", "demo", "playground", "template",
                "starter", "website", "web app", "dashboard", "canvas", "game", "ui", "前端", "可视化");
        boolean headlessCore = containsAny(evidence, "sdk", "library", "framework", "protocol",
                "compiler", "runtime", "infrastructure", "dependency", "release automation", "cli");
        if (headlessCore && !visualOutcome) {
            showcase = Math.min(showcase, 3);
            content = Math.min(content, 3);
        }

        // A model-provided 5 is exceptional. Keep at most two perfect dimensions so a generic
        // enthusiastic response cannot turn every repository into a 100-point candidate.
        List<Integer> values = new ArrayList<>(List.of(vibe, showcase, extension, scope, content));
        int perfect = 0;
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) == 5 && ++perfect > 2) values.set(i, 4);
        }
        return new Ratings(values.get(0), values.get(1), values.get(2), values.get(3), values.get(4));
    }

    private int bounded(int value) {
        return Math.max(1, Math.min(5, value));
    }

    private int countTrue(boolean... values) {
        int count = 0;
        for (boolean value : values) if (value) count++;
        return count;
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    public record Ratings(int vibeCodingFit, int showcaseValue, int extensionPotential,
                          int scopeClarity, int contentPotential) {
    }

    public record Signals(int technicalScore, boolean hasQuickStart, String codebaseSize,
                          boolean requiresDocker, boolean requiresDatabase, boolean requiresSecrets,
                          boolean requiresExternalService, boolean requiresGpu,
                          boolean requiresNativeBuild, String searchableText) {
    }
}
