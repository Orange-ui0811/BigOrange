ALTER TABLE persona_equipment
    ADD COLUMN blueprint_json JSON NULL AFTER transform_json,
    ADD COLUMN blueprint_version INT NOT NULL DEFAULT 0 AFTER blueprint_json;
