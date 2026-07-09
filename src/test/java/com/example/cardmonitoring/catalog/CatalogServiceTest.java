package com.example.cardmonitoring.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.cardmonitoring.cardtrader.CardTraderBlueprint;
import com.example.cardmonitoring.cardtrader.CardTraderCategory;
import com.example.cardmonitoring.cardtrader.CardTraderClient;
import com.example.cardmonitoring.cardtrader.CardTraderException;
import com.example.cardmonitoring.cardtrader.CardTraderExpansion;
import com.example.cardmonitoring.cardtrader.CardTraderGame;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

	@Mock
	private CardTraderClient cardTraderClient;

	private CatalogService catalogService;

	@BeforeEach
	void setUp() {
		catalogService = new CatalogService(cardTraderClient);
	}

	@Test
	void filtersSortsAndCachesPokemonExpansions() {
		stubValidPokemonCatalog();
		when(cardTraderClient.getExpansions()).thenReturn(List.of(
				new CardTraderExpansion(1, "Alpha", "a", 1),
				new CardTraderExpansion(1478, "Base Set 2", "b2", 5),
				new CardTraderExpansion(1472, "Base Set", "bs", 5)));

		List<CatalogExpansion> firstCall = catalogService.getPokemonExpansions();
		List<CatalogExpansion> secondCall = catalogService.getPokemonExpansions();

		assertThat(firstCall).containsExactly(
				new CatalogExpansion(1472, "Base Set", "bs"),
				new CatalogExpansion(1478, "Base Set 2", "b2"));
		assertThat(secondCall).isSameAs(firstCall);
		verify(cardTraderClient).getGames();
		verify(cardTraderClient).getCategories(5);
		verify(cardTraderClient).getExpansions();
	}

	@Test
	void filtersSortsAndCachesBlueprintsForSelectedExpansion() {
		stubBaseSetExpansion();
		when(cardTraderClient.getBlueprints(1472)).thenReturn(List.of(
				new CardTraderBlueprint(2, "Wrong category", null, 1, 1472),
				new CardTraderBlueprint(3, "Wrong expansion", null, 73, 1478),
				new CardTraderBlueprint(111151, "Charizard", "Holo Rare | 4/102", 73, 1472),
				new CardTraderBlueprint(111125, "Pikachu", "Trainer Gallery Rare | TG01/TG30", 73, 1472),
				new CardTraderBlueprint(111160, "Promo card", "Promo", 73, 1472),
				new CardTraderBlueprint(111100, "Abra", "Common | 43/102", 73, 1472)));

		List<CatalogBlueprint> firstCall = catalogService.getPokemonBlueprints(1472);
		List<CatalogBlueprint> secondCall = catalogService.getPokemonBlueprints(1472);

		assertThat(firstCall).containsExactly(
				new CatalogBlueprint(111125, "Pikachu", "Trainer Gallery Rare | TG01/TG30", 1472),
				new CatalogBlueprint(111151, "Charizard", "Holo Rare | 4/102", 1472),
				new CatalogBlueprint(111100, "Abra", "Common | 43/102", 1472),
				new CatalogBlueprint(111160, "Promo card", "Promo", 1472));
		assertThat(secondCall).isSameAs(firstCall);
		verify(cardTraderClient).getBlueprints(1472);
	}

	@Test
	void rejectsExpansionOutsidePokemonCatalog() {
		stubBaseSetExpansion();

		assertThatThrownBy(() -> catalogService.getPokemonBlueprints(9999))
				.isInstanceOf(CatalogNotFoundException.class);
		verify(cardTraderClient, never()).getBlueprints(9999);
	}

	@Test
	void resolvesCardIdentityFromValidatedPokemonCatalog() {
		stubBaseSetExpansion();
		when(cardTraderClient.getBlueprints(1472)).thenReturn(List.of(
				new CardTraderBlueprint(111151, "Charizard", "Holo Rare | 4/102", 73, 1472)));

		assertThat(catalogService.resolvePokemonCard(1472, 111151)).isEqualTo(
				new CatalogCard(111151, "Charizard", "Holo Rare | 4/102", 1472, "Base Set", "bs"));
	}

	@Test
	void rejectsCatalogWithoutExpectedSinglesCategory() {
		when(cardTraderClient.getGames()).thenReturn(List.of(new CardTraderGame(5, "Pokémon", "Pokémon")));
		when(cardTraderClient.getCategories(5)).thenReturn(List.of(new CardTraderCategory(72, "Other", 5)));

		assertThatThrownBy(catalogService::getPokemonExpansions)
				.isInstanceOfSatisfying(CardTraderException.class, exception ->
						assertThat(exception.getReason()).isEqualTo(CardTraderException.Reason.INVALID_RESPONSE));
		verify(cardTraderClient, never()).getExpansions();
	}

	private void stubBaseSetExpansion() {
		stubValidPokemonCatalog();
		when(cardTraderClient.getExpansions()).thenReturn(List.of(
				new CardTraderExpansion(1472, "Base Set", "bs", 5)));
	}

	private void stubValidPokemonCatalog() {
		when(cardTraderClient.getGames()).thenReturn(List.of(
				new CardTraderGame(5, "Pokémon", "Pokémon")));
		when(cardTraderClient.getCategories(5)).thenReturn(List.of(
				new CardTraderCategory(73, "Pokémon Singles", 5)));
	}
}
