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
}
