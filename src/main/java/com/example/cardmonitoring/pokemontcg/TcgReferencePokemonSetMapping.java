package com.example.cardmonitoring.pokemontcg;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tcg_reference_pokemon_set_mapping")
public class TcgReferencePokemonSetMapping {

	@Id
	@Column(name = "cardtrader_expansion_id")
	private long cardTraderExpansionId;

	@Column(name = "reference_set_id", nullable = false)
	private long referenceSetId;

	@Column(name = "pokemon_tcg_set_id", length = 100)
	private String pokemonTcgSetId;

	@Column(name = "mapping_status", nullable = false, length = 30)
	private String mappingStatus;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected TcgReferencePokemonSetMapping() {
	}

	private TcgReferencePokemonSetMapping(
			long cardTraderExpansionId,
			long referenceSetId,
			String pokemonTcgSetId,
			String mappingStatus,
			Instant now) {
		this.cardTraderExpansionId = cardTraderExpansionId;
		this.referenceSetId = referenceSetId;
		this.pokemonTcgSetId = pokemonTcgSetId;
		this.mappingStatus = mappingStatus;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static TcgReferencePokemonSetMapping mapped(
			long cardTraderExpansionId, long referenceSetId, String pokemonTcgSetId, Instant now) {
		return new TcgReferencePokemonSetMapping(cardTraderExpansionId, referenceSetId, pokemonTcgSetId,
				TcgReferencePokemonSetMappingStatus.MAPPED.name(), now);
	}

	public static TcgReferencePokemonSetMapping unmappable(long cardTraderExpansionId, long referenceSetId, Instant now) {
		return new TcgReferencePokemonSetMapping(cardTraderExpansionId, referenceSetId, null,
				TcgReferencePokemonSetMappingStatus.UNMAPPABLE.name(), now);
	}

	public boolean appliesTo(long referenceSetId) {
		return this.referenceSetId == referenceSetId;
	}

	public boolean isMapped() {
		return TcgReferencePokemonSetMappingStatus.MAPPED.name().equals(mappingStatus);
	}

	public String getPokemonTcgSetId() {
		return pokemonTcgSetId;
	}
}
