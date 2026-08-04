ALTER TABLE persona_workshop_setting
    ADD COLUMN body_color CHAR(7) NOT NULL DEFAULT '#F37021' AFTER avatar_enabled,
    ADD COLUMN face_style VARCHAR(24) NOT NULL DEFAULT 'FRIENDLY' AFTER body_color,
    ADD COLUMN leaf_style VARCHAR(24) NOT NULL DEFAULT 'CLASSIC' AFTER face_style,
    ADD COLUMN surface_style VARCHAR(24) NOT NULL DEFAULT 'NATURAL' AFTER leaf_style;
