package com.example.cardmonitoring.pokemontcg;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "pokemon_tcg_set_mapping")
public class PokemonTcgSetMapping {
	@Id
	@Column(name = "cardtrader_expansion_id")
	private long cardTraderExpansionId;
	@Column(name = "pokemon_tcg_set_id", nullable = false, length = 100)
	private String pokemonTcgSetId;
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
	protected PokemonTcgSetMapping() { }
	public PokemonTcgSetMapping(long cardTraderExpansionId, String pokemonTcgSetId, Instant now) {
		this.cardTraderExpansionId = cardTraderExpansionId;
		this.pokemonTcgSetId = pokemonTcgSetId;
		this.createdAt = now;
		this.updatedAt = now;
	}
	public String getPokemonTcgSetId() { return pokemonTcgSetId; }
}
