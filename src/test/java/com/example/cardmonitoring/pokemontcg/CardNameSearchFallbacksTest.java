package com.example.cardmonitoring.pokemontcg;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CardNameSearchFallbacksTest {

	@Test
	void createsRequestedFallbackSpellingsWithoutChangingTheOriginalName() {
		assertThat(CardNameSearchFallbacks.alternatives("Boss's Orders - Corbeau"))
				.contains("Boss's Orders");
		assertThat(CardNameSearchFallbacks.alternatives("Umbreon Star")).contains("Umbreon");
		assertThat(CardNameSearchFallbacks.alternatives("Tapu Lele GX")).contains("Tapu Lele-GX");
		assertThat(CardNameSearchFallbacks.alternatives("Jirachi ◇ Prism Star"))
				.contains("Jirachi ◇", "Jirachi");
		assertThat(CardNameSearchFallbacks.alternatives("Alakazam ☆ Gold Star"))
				.contains("Alakazam ☆", "Alakazam");
		assertThat(CardNameSearchFallbacks.alternatives("Bubbly [W] Energy"))
				.contains("Bubbly Water Energy");
		assertThat(CardNameSearchFallbacks.alternatives("Charizard δ Delta Species"))
				.contains("Charizard");
		assertThat(CardNameSearchFallbacks.alternatives("Miracle Sphere α Alpha"))
				.contains("Miracle Sphere");
		assertThat(CardNameSearchFallbacks.alternatives("Gallade 4")).contains("Gallade E4");
	}
}
