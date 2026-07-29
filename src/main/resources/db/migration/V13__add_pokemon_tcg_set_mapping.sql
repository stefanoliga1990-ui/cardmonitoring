CREATE TABLE pokemon_tcg_set_mapping (
    cardtrader_expansion_id BIGINT PRIMARY KEY,
    pokemon_tcg_set_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_pokemon_tcg_set_mapping_expansion CHECK (cardtrader_expansion_id > 0)
);
