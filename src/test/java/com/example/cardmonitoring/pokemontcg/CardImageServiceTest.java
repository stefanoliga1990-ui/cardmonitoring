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

	@Mock
	private PokemonTcgSetImageService pokemonTcgSetImageService;

	@Mock
	private TcgCollectorReferenceCatalogService referenceCatalogService;

	@Test
	void retriesWithHyphenatedExNameWhenTheFirstSearchHasNoCandidates() {
		CatalogCard card = new CatalogCard(84, "Sceptile EX", "Ultra Rare | 84/98", 1573, "Ancient Origins", "aor");
		PokemonTcgCardCandidate candidate = new PokemonTcgCardCandidate(
				"xy7-84", "Sceptile-EX", "84", "xy7", "Ancient Origins", null, 98, 100, null,
				"https://images.test/sceptile-small.png", "https://images.test/sceptile-large.png");

		when(cardImageRepository.findByExpansionIdAndBlueprintIdAndCollectorNumberAndImageSource(
				1573, 84, "84", "POKEMON_TCG_API")).thenReturn(Optional.empty());
		when(pokemonTcgSetImageService.findCandidates(card, "84")).thenReturn(List.of());
		when(pokemonTcgClient.searchCards("name:\"Sceptile EX\" number:84")).thenReturn(List.of());
		when(pokemonTcgClient.searchCards("name:\"Sceptile-EX\" number:84")).thenReturn(List.of(candidate));
		when(cardImageRepository.saveAndFlush(any(StoredCardImage.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		Optional<CardImage> image = new CardImageService(pokemonTcgClient, pokemonTcgSetImageService, referenceCatalogService, cardImageRepository)
				.resolve(card);

		assertThat(image).isPresent();
		assertThat(image.get().externalCardId()).isEqualTo("xy7-84");
		verify(pokemonTcgClient).searchCards(eq("name:\"Sceptile EX\" number:84"));
		verify(pokemonTcgClient).searchCards(eq("name:\"Sceptile-EX\" number:84"));
	}

	@Test
	void usesMappedSetAndCollectorNumberBeforeCardName() {
		CatalogCard card = new CatalogCard(96, "M Primal Kyogre ex", "Ultra Rare | 96/98", 1573, "Ancient Origins", "aor");
		PokemonTcgCardCandidate candidate = new PokemonTcgCardCandidate("xy7-96", "Primal Kyogre-EX", "96", "xy7",
				"Ancient Origins", null, 98, 100, null, "https://images.test/s.png", "https://images.test/l.png");
		when(cardImageRepository.findByExpansionIdAndBlueprintIdAndCollectorNumberAndImageSource(
				1573, 96, "96", "POKEMON_TCG_API")).thenReturn(Optional.empty());
		when(pokemonTcgSetImageService.findCandidates(card, "96")).thenReturn(List.of(candidate));
		when(cardImageRepository.saveAndFlush(any(StoredCardImage.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Optional<CardImage> image = new CardImageService(pokemonTcgClient, pokemonTcgSetImageService, referenceCatalogService, cardImageRepository)
				.resolve(card);

		assertThat(image).isPresent();
		assertThat(image.get().externalCardId()).isEqualTo("xy7-96");
	}

	@Test
	void usesUniqueNameInsideMappedSetWhenVersionHasNoReliableCollectorNumber() {
		CatalogCard card = new CatalogCard(2017, "Fire Energy", "2017 | Charizard Stamp 2", 2000,
				"Battle Academy 2020", "ba-20");
		PokemonTcgCardCandidate candidate = new PokemonTcgCardCandidate("sm1-167", "Fire Energy", "167", "sm1",
				"Sun & Moon", null, 149, 173, null, "https://images.test/s.png", "https://images.test/l.png");
		when(cardImageRepository.findByExpansionIdAndBlueprintIdAndCollectorNumberAndImageSource(
				2000, 2017, "__NO_COLLECTOR_NUMBER__", "POKEMON_TCG_API")).thenReturn(Optional.empty());
		when(pokemonTcgSetImageService.findCandidatesByName(card)).thenReturn(List.of(candidate));
		when(cardImageRepository.saveAndFlush(any(StoredCardImage.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Optional<CardImage> image = new CardImageService(pokemonTcgClient, pokemonTcgSetImageService, referenceCatalogService, cardImageRepository)
				.resolve(card);

		assertThat(image).isPresent();
		assertThat(image.get().externalCardId()).isEqualTo("sm1-167");
		verify(pokemonTcgSetImageService).findCandidatesByName(card);
	}

	@Test
	void usesGloballyUniqueNameOnlyAfterMappedSetFallbackFails() {
		CatalogCard card = new CatalogCard(2018, "Uncommon Example", "2017 | Charizard Stamp 2", 2000,
				"Battle Academy 2020", "ba-20");
		PokemonTcgCardCandidate candidate = new PokemonTcgCardCandidate("sm1-168", "Uncommon Example", "168", "sm1",
				"Sun & Moon", null, 149, 173, null, "https://images.test/s.png", "https://images.test/l.png");
		when(cardImageRepository.findByExpansionIdAndBlueprintIdAndCollectorNumberAndImageSource(
				2000, 2018, "__NO_COLLECTOR_NUMBER__", "POKEMON_TCG_API")).thenReturn(Optional.empty());
		when(pokemonTcgSetImageService.findCandidatesByName(card)).thenReturn(List.of());
		when(pokemonTcgClient.searchSingleCard("name:\"Uncommon Example\"")).thenReturn(Optional.of(candidate));
		when(cardImageRepository.saveAndFlush(any(StoredCardImage.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Optional<CardImage> image = new CardImageService(pokemonTcgClient, pokemonTcgSetImageService, referenceCatalogService, cardImageRepository)
				.resolve(card);

		assertThat(image).isPresent();
		assertThat(image.get().externalCardId()).isEqualTo("sm1-168");
		verify(pokemonTcgClient).searchSingleCard("name:\"Uncommon Example\"");
	}

	@Test
	void doesNotAcceptMappedSetNumberWhenTheNameDoesNotMatch() {
		CatalogCard card = new CatalogCard(255, "Boss's Orders - Corbeau", "Ultra Rare | 255/217", 3000,
				"Ascended Heroes", "asc");
		PokemonTcgCardCandidate wrongNumberCandidate = new PokemonTcgCardCandidate("asc-255", "Black Belt's Training",
				"255", "asc", "Ascended Heroes", null, 217, 217, null, "https://images.test/wrong-s.png", "https://images.test/wrong-l.png");
		PokemonTcgCardCandidate correctNameCandidate = new PokemonTcgCardCandidate("asc-256", "Boss's Orders",
				"256", "asc", "Ascended Heroes", null, 217, 217, null, "https://images.test/right-s.png", "https://images.test/right-l.png");
		when(cardImageRepository.findByExpansionIdAndBlueprintIdAndCollectorNumberAndImageSource(
				3000, 255, "255", "POKEMON_TCG_API")).thenReturn(Optional.empty());
		when(pokemonTcgSetImageService.findCandidates(card, "255")).thenReturn(List.of(wrongNumberCandidate));
		when(pokemonTcgSetImageService.findCandidatesByName(card)).thenReturn(List.of(correctNameCandidate));
		when(cardImageRepository.saveAndFlush(any(StoredCardImage.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Optional<CardImage> image = new CardImageService(pokemonTcgClient, pokemonTcgSetImageService, referenceCatalogService, cardImageRepository)
				.resolve(card);

		assertThat(image).isPresent();
		assertThat(image.get().externalCardId()).isEqualTo("asc-256");
	}

	@Test
	void usesReferenceCatalogNumberWhenCardTraderNumberPointsToAnotherCard() {
		CatalogCard card = new CatalogCard(255, "Boss's Orders - Corbeau", "Ultra Rare | 255/217", 3000,
				"Ascended Heroes", "asc");
		PokemonTcgCardCandidate candidate = new PokemonTcgCardCandidate("asc-256", "Boss's Orders", "256", "asc",
				"Ascended Heroes", null, 217, 217, null, "https://images.test/right-s.png", "https://images.test/right-l.png");
		TcgCollectorReferenceCatalogService.ReferenceCardMatch reference =
				new TcgCollectorReferenceCatalogService.ReferenceCardMatch(
						42L, "Ascended Heroes", "Boss's Orders", "256",
						TcgCollectorReferenceCatalogService.MatchConfidence.UNIQUE_NAME);
		when(cardImageRepository.findByExpansionIdAndBlueprintIdAndCollectorNumberAndImageSource(
				3000, 255, "255", "POKEMON_TCG_API")).thenReturn(Optional.empty());
		when(referenceCatalogService.findMatch(card)).thenReturn(Optional.of(reference));
		when(pokemonTcgSetImageService.findCandidates(card, "256", Optional.of(reference))).thenReturn(List.of(candidate));
		when(cardImageRepository.saveAndFlush(any(StoredCardImage.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Optional<CardImage> image = new CardImageService(
				pokemonTcgClient, pokemonTcgSetImageService, referenceCatalogService, cardImageRepository).resolve(card);

		assertThat(image).isPresent();
		assertThat(image.get().externalCardId()).isEqualTo("asc-256");
		verify(pokemonTcgSetImageService).findCandidates(card, "256", Optional.of(reference));
	}
}
