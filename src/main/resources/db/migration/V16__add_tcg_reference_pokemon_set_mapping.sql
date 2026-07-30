CREATE TABLE tcg_reference_pokemon_set_mapping (
    cardtrader_expansion_id BIGINT PRIMARY KEY,
    reference_set_id BIGINT NOT NULL,
    pokemon_tcg_set_id VARCHAR(100),
    mapping_status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_tcg_reference_pokemon_mapping_set
        FOREIGN KEY (reference_set_id) REFERENCES tcg_reference_set (id),
    CONSTRAINT chk_tcg_reference_pokemon_mapping_expansion CHECK (cardtrader_expansion_id > 0),
    CONSTRAINT chk_tcg_reference_pokemon_mapping_status CHECK (mapping_status IN ('MAPPED', 'UNMAPPABLE')),
    CONSTRAINT chk_tcg_reference_pokemon_mapping_value CHECK (
        (mapping_status = 'MAPPED' AND pokemon_tcg_set_id IS NOT NULL)
        OR (mapping_status = 'UNMAPPABLE' AND pokemon_tcg_set_id IS NULL)
    )
);
