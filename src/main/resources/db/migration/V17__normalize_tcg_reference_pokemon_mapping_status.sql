ALTER TABLE tcg_reference_pokemon_set_mapping
    DROP CONSTRAINT chk_tcg_reference_pokemon_mapping_value;

ALTER TABLE tcg_reference_pokemon_set_mapping
    DROP CONSTRAINT chk_tcg_reference_pokemon_mapping_status;

ALTER TABLE tcg_reference_pokemon_set_mapping
    ALTER COLUMN mapping_status VARCHAR(30) NOT NULL;

ALTER TABLE tcg_reference_pokemon_set_mapping
    ADD CONSTRAINT chk_tcg_reference_pokemon_mapping_status
        CHECK (mapping_status IN ('MAPPED', 'UNMAPPABLE'));

ALTER TABLE tcg_reference_pokemon_set_mapping
    ADD CONSTRAINT chk_tcg_reference_pokemon_mapping_value CHECK (
        (mapping_status = 'MAPPED' AND pokemon_tcg_set_id IS NOT NULL)
        OR (mapping_status = 'UNMAPPABLE' AND pokemon_tcg_set_id IS NULL)
    );
