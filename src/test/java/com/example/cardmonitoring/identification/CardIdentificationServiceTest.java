package com.example.cardmonitoring.identification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.cardmonitoring.catalog.CatalogBlueprint;
import com.example.cardmonitoring.catalog.CatalogCard;
import com.example.cardmonitoring.catalog.CatalogExpansion;
import com.example.cardmonitoring.catalog.CatalogService;
import com.example.cardmonitoring.pokemontcg.CardImage;
import com.example.cardmonitoring.pokemontcg.CardImageService;
import com.example.cardmonitoring.pokemontcg.PokemonTcgCardCandidate;
import com.example.cardmonitoring.pokemontcg.PokemonTcgClient;

@ExtendWith(MockitoExtension.class)
class CardIdentificationServiceTest {

	@Mock
	private PokemonTcgClient pokemonTcgClient;

	@Mock
	private CatalogService catalogService;

	@Mock
	private CardImageService cardImageService;

	private CardIdentificationService service;

	@BeforeEach
	void setUp() {
		service = new CardIdentificationService(pokemonTcgClient, catalogService, cardImageService);
	}

	@Test
	void findsSelectableCardByNameNumberAndTotal() {
		when(pokemonTcgClient.searchCards("name:\"CHARIZARD\" number:4", 80)).thenReturn(List.of(
				candidate("base1-4", "Charizard", "4", "Base", 102, 102)));
		when(catalogService.getPokemonExpansions()).thenReturn(List.of(
				new CatalogExpansion(1472, "Base Set", "bs")));
		when(catalogService.getPokemonBlueprints(1472)).thenReturn(List.of(
				new CatalogBlueprint(111151, "Charizard", "Holo Rare | 4/102", 1472)));

		List<CardIdentificationCandidate> candidates = service.findCandidates(
				new CardIdentificationRequest("Charizard", "004", "102"));

		assertThat(candidates).singleElement().satisfies(candidate -> {
			assertThat(candidate.selectable()).isTrue();
			assertThat(candidate.matchConfidence()).isEqualTo("HIGH");
			assertThat(candidate.displayNumber()).isEqualTo("4/102");
			assertThat(candidate.cardTraderExpansionId()).isEqualTo(1472);
			assertThat(candidate.cardTraderBlueprintId()).isEqualTo(111151);
			assertThat(candidate.imageUrlSmall()).isEqualTo("https://images.test/base1-4-small.png");
		});
		verify(cardImageService).cacheResolvedImage(
				new CatalogCard(111151, "Charizard", "Holo Rare | 4/102", 1472, "Base Set", "bs"),
				"4",
				new CardImage(
						"https://images.test/base1-4-small.png",
						"https://images.test/base1-4-large.png",
						"POKEMON_TCG_API",
						"base1-4"));
	}

	@Test
	void acceptsNumberAlreadyWrittenAsFraction() {
		when(pokemonTcgClient.searchCards("name:\"CHARIZARD\" number:4", 80)).thenReturn(List.of(
				candidate("base1-4", "Charizard", "4", "Base", 102, 102)));
		when(catalogService.getPokemonExpansions()).thenReturn(List.of(
				new CatalogExpansion(1472, "Base Set", "bs")));
		when(catalogService.getPokemonBlueprints(1472)).thenReturn(List.of(
				new CatalogBlueprint(111151, "Charizard", "Holo Rare | 4/102", 1472)));

		List<CardIdentificationCandidate> candidates = service.findCandidates(
				new CardIdentificationRequest("Charizard", "4/102", null));

		assertThat(candidates).singleElement().satisfies(candidate -> {
			assertThat(candidate.displayNumber()).isEqualTo("4/102");
			assertThat(candidate.selectable()).isTrue();
		});
	}

	@Test
	void preservesAlphanumericNumbersForPokemonTcgSearch() {
		when(pokemonTcgClient.searchCards("name:\"PIKACHU\" number:SWSH020", 80)).thenReturn(List.of(
				candidate("swshp-SWSH020", "Pikachu", "SWSH020", "SWSH Black Star Promos", null, null)));
		when(catalogService.getPokemonExpansions()).thenReturn(List.of(
				new CatalogExpansion(2000, "SWSH Black Star Promos", "swshp")));
		when(catalogService.getPokemonBlueprints(2000)).thenReturn(List.of(
				new CatalogBlueprint(222, "Pikachu", "Promo | SWSH020", 2000)));

		List<CardIdentificationCandidate> candidates = service.findCandidates(
				new CardIdentificationRequest("Pikachu", "swsh020", ""));

		assertThat(candidates).singleElement().satisfies(candidate -> {
			assertThat(candidate.displayNumber()).isEqualTo("SWSH020");
			assertThat(candidate.selectable()).isTrue();
			assertThat(candidate.matchConfidence()).isEqualTo("MEDIUM");
		});
	}

	@Test
	void keepsVisualCandidateButDoesNotSelectWhenCardTraderMappingIsUnsafe() {
		when(pokemonTcgClient.searchCards("name:\"NINETALES\" number:12", 80)).thenReturn(List.of(
				candidate("base1-12", "Ninetales", "12", "Base", 102, 102)));
		when(catalogService.getPokemonExpansions()).thenReturn(List.of(
				new CatalogExpansion(1472, "Base Set", "bs")));
		when(catalogService.getPokemonBlueprints(1472)).thenReturn(List.of(
				new CatalogBlueprint(111151, "Charizard", "Holo Rare | 4/102", 1472)));

		List<CardIdentificationCandidate> candidates = service.findCandidates(
				new CardIdentificationRequest("Ninetales", "12", "102"));

		assertThat(candidates).singleElement().satisfies(candidate -> {
			assertThat(candidate.selectable()).isFalse();
			assertThat(candidate.matchConfidence()).isEqualTo("LOW");
			assertThat(candidate.cardTraderBlueprintId()).isNull();
			assertThat(candidate.imageUrlSmall()).isEqualTo("https://images.test/base1-12-small.png");
		});
		verify(cardImageService, never()).cacheResolvedImage(any(CatalogCard.class), any(), any(CardImage.class));
	}

	@Test
	void usesCachedCandidatesForRepeatedSearches() {
		when(pokemonTcgClient.searchCards("name:\"CHARIZARD\" number:4", 80)).thenReturn(List.of(
				candidate("base1-4", "Charizard", "4", "Base", 102, 102)));
		when(catalogService.getPokemonExpansions()).thenReturn(List.of(
				new CatalogExpansion(1472, "Base Set", "bs")));
		when(catalogService.getPokemonBlueprints(1472)).thenReturn(List.of(
				new CatalogBlueprint(111151, "Charizard", "Holo Rare | 4/102", 1472)));

		service.findCandidates(new CardIdentificationRequest("Charizard", "4", "102"));
		service.findCandidates(new CardIdentificationRequest("Charizard", "4", "102"));

		verify(pokemonTcgClient).searchCards("name:\"CHARIZARD\" number:4", 80);
		verify(pokemonTcgClient, never()).searchCards("name:\"CHARIZARD\"", 120);
		verify(cardImageService).cacheResolvedImage(any(CatalogCard.class), eq("4"), any(CardImage.class));
	}

	private static PokemonTcgCardCandidate candidate(
			String id,
			String name,
			String number,
			String setName,
			Integer printedTotal,
			Integer total) {
		return new PokemonTcgCardCandidate(
				id,
				name,
				number,
				id.substring(0, id.indexOf('-')),
				setName,
				"Base series",
				printedTotal,
				total,
				"1999/01/09",
				"https://images.test/" + id + "-small.png",
				"https://images.test/" + id + "-large.png");
	}
}
