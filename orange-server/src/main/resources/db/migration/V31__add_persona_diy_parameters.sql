ALTER TABLE persona_workshop_setting
    ADD COLUMN face_config JSON NULL AFTER surface_style;

ALTER TABLE persona_equipment
    ADD COLUMN transform_json JSON NULL AFTER equipped;
