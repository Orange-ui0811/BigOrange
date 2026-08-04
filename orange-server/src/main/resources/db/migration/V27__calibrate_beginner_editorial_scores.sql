-- Recalibrate legacy AI ratings with deterministic repository evidence.
UPDATE beginner_project
SET vibe_coding_fit = LEAST(vibe_coding_fit,
        CASE
            WHEN beginner_score < 75 OR
                 (COALESCE(requires_docker, 0) + COALESCE(requires_database, 0) +
                  COALESCE(requires_secrets, 0) + COALESCE(requires_external_service, 0) +
                  COALESCE(requires_gpu, 0) + COALESCE(requires_native_build, 0)) >= 2 THEN 3
            WHEN (COALESCE(requires_docker, 0) + COALESCE(requires_database, 0) +
                  COALESCE(requires_secrets, 0) + COALESCE(requires_external_service, 0) +
                  COALESCE(requires_gpu, 0) + COALESCE(requires_native_build, 0)) = 1 THEN 4
            ELSE 5
        END),
    showcase_value = CASE WHEN COALESCE(has_quick_start, 0) = 0
                          THEN LEAST(showcase_value, 3) ELSE showcase_value END,
    extension_potential = CASE WHEN codebase_size = 'LARGE' OR COALESCE(requires_native_build, 0) = 1
                               THEN LEAST(extension_potential, 3) ELSE extension_potential END,
    scope_clarity = CASE
        WHEN codebase_size = 'LARGE' THEN LEAST(scope_clarity, 2)
        WHEN COALESCE(has_quick_start, 0) = 0 THEN LEAST(scope_clarity, 3)
        WHEN codebase_size = 'MEDIUM' THEN LEAST(scope_clarity, 4)
        ELSE scope_clarity
    END
WHERE editorial_score IS NOT NULL;

-- Keep no more than two perfect dimensions. Assignments are intentionally ordered.
UPDATE beginner_project
SET extension_potential = CASE WHEN (vibe_coding_fit = 5) + (showcase_value = 5) >= 2
                               THEN LEAST(extension_potential, 4) ELSE extension_potential END,
    scope_clarity = CASE WHEN (vibe_coding_fit = 5) + (showcase_value = 5) +
                                  (extension_potential = 5) >= 2
                         THEN LEAST(scope_clarity, 4) ELSE scope_clarity END,
    content_potential = CASE WHEN (vibe_coding_fit = 5) + (showcase_value = 5) +
                                      (extension_potential = 5) + (scope_clarity = 5) >= 2
                             THEN LEAST(content_potential, 4) ELSE content_potential END
WHERE editorial_score IS NOT NULL;

UPDATE beginner_project
SET editorial_score = (vibe_coding_fit * 30 + showcase_value * 25 +
                       extension_potential * 15 + scope_clarity * 15 +
                       content_potential * 15) DIV 5,
    status = CASE
        WHEN status IN ('HIDDEN', 'INVALID') THEN status
        WHEN beginner_score >= 65 AND vibe_coding_fit >= 4 AND showcase_value >= 4 AND
             extension_potential >= 4 AND scope_clarity >= 4 AND content_potential >= 4
             THEN 'PUBLISHED'
        ELSE 'CANDIDATE'
    END,
    score_version = 'beginner-v3'
WHERE editorial_score IS NOT NULL;
