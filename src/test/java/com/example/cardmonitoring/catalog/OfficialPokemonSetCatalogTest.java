package com.example.cardmonitoring.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OfficialPokemonSetCatalogTest {

	@Test
	void includesOfficialNamesDespitePunctuationAndAccents() {
		assertThat(OfficialPokemonSetCatalog.includes("Base Set Shadowless", "shbs")).isTrue();
		assertThat(OfficialPokemonSetCatalog.includes("Pokémon Rumble", "rmbl")).isTrue();
		assertThat(OfficialPokemonSetCatalog.includes("Wizards Black Star Promos", "wiz")).isTrue();
	}

	@Test
	void includesOnlyVerifiedCardTraderAliases() {
		assertThat(OfficialPokemonSetCatalog.includes("Pokémon TCG: Pokémon GO", "pkmgo")).isTrue();
		assertThat(OfficialPokemonSetCatalog.includes("World Championship Decks 2025", "wcd2025")).isTrue();
		assertThat(OfficialPokemonSetCatalog.includes("Pokémon Products", "popr")).isFalse();
	}

	@Test
	void excludesKnownCardTraderExpansionWithoutPokemonSingles() {
		assertThat(OfficialPokemonSetCatalog.includes("30th Celebration", "30c")).isFalse();
	}

	@Test
	void excludesTrainerKitSetsEvenWhenTheirCardTraderCodeIsAnAlias() {
		assertThat(OfficialPokemonSetCatalog.includes("XY Trainer Kit (Wigglytuff)", "wigglytuff")).isFalse();
		assertThat(OfficialPokemonSetCatalog.includes("SM Trainer Kit (Lycanroc)", "tk11")).isFalse();
	}

	@Test
	void excludesTheRequestedDeckAndPromoSets() {
		assertThat(OfficialPokemonSetCatalog.includes("Battle Academy 2020", "ba-20")).isFalse();
		assertThat(OfficialPokemonSetCatalog.includes("McDonald's Collection 2018 French", "unknown")).isFalse();
		assertThat(OfficialPokemonSetCatalog.includes("Nintendo Black Star Promos", "nbsp")).isFalse();
		assertThat(OfficialPokemonSetCatalog.includes("Play! Pokémon Prize Pack Series", "playprizep")).isFalse();
		assertThat(OfficialPokemonSetCatalog.includes(
				"Pokémon TCG Classic: Charizard & Ho-Oh ex Deck", "clc")).isFalse();
		assertThat(OfficialPokemonSetCatalog.includes("SWSH Black Star Promos", "swshbs")).isFalse();
	}

	@Test
	void excludesWorldChampionshipDecksThrough2023ButKeepsLaterSets() {
		assertThat(OfficialPokemonSetCatalog.includes("World Championship Decks 2004", "wcd2004")).isFalse();
		assertThat(OfficialPokemonSetCatalog.includes("World Championship Decks 2023", "wcd2023")).isFalse();
		assertThat(OfficialPokemonSetCatalog.includes("World Championship Decks 2024", "wcd2024")).isTrue();
		assertThat(OfficialPokemonSetCatalog.includes("World Championship Decks 2025", "wcd2025")).isTrue();
	}
}
