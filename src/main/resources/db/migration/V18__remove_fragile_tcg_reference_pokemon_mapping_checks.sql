ALTER TABLE tcg_reference_pokemon_set_mapping
    DROP CONSTRAINT IF EXISTS chk_tcg_reference_pokemon_mapping_value;

ALTER TABLE tcg_reference_pokemon_set_mapping
    DROP CONSTRAINT IF EXISTS chk_tcg_reference_pokemon_mapping_status;
