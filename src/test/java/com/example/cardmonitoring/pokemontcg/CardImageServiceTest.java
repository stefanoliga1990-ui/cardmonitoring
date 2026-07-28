package com.example.cardmonitoring.pokemontcg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.cardmonitoring.catalog.CatalogCard;

@ExtendWith(MockitoExtension.class)
class CardImageServiceTest {

	@Mock
	private PokemonTcgClient pokemonTcgClient;

	@Mock
	private CardImageRepository cardImageRepository;

	@Test
	void retriesWithHyphenatedExNameWhenTheFirstSearchHasNoCandidates() {
		CatalogCard card = new CatalogCard(84, "Sceptile EX", "Ultra Rare | 84/98", 1573, "Ancient Origins", "aor");
		PokemonTcgCardCandidate candidate = new PokemonTcgCardCandidate(
				"xy7-84", "Sceptile-EX", "84", "xy7", "Ancient Origins", null, 98, 100, null,
				"https://images.test/sceptile-small.png", "https://images.test/sceptile-large.png");

		when(cardImageRepository.findByExpansionIdAndBlueprintIdAndCollectorNumberAndImageSource(
				1573, 84, "84", "POKEMON_TCG_API")).thenReturn(Optional.empty());
		when(pokemonTcgClient.searchCards("name:\"Sceptile EX\" number:84")).thenReturn(List.of());
		when(pokemonTcgClient.searchCards("name:\"Sceptile-EX\" number:84")).thenReturn(List.of(candidate));
		when(cardImageRepository.saveAndFlush(any(StoredCardImage.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		Optional<CardImage> image = new CardImageService(pokemonTcgClient, cardImageRepository).resolve(card);

		assertThat(image).isPresent();
		assertThat(image.get().externalCardId()).isEqualTo("xy7-84");
		verify(pokemonTcgClient).searchCards(eq("name:\"Sceptile EX\" number:84"));
		verify(pokemonTcgClient).searchCards(eq("name:\"Sceptile-EX\" number:84"));
	}
}
