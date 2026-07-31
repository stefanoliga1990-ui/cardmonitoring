package com.example.cardmonitoring.pokemontcg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.cardmonitoring.catalog.CatalogCard;

@ExtendWith(MockitoExtension.class)
class TcgReferencePokemonSetMappingServiceTest {

	@Mock
	private TcgReferencePokemonSetMappingRepository mappingRepository;
	@Mock
	private TcgCollectorReferenceCatalogService referenceCatalogService;
	@Mock
	private PokemonTcgClient pokemonTcgClient;

	@Test
	void persistsSetMappingOnlyAfterTwoReferenceCardsAgree() {
		CatalogCard card = new CatalogCard(255, "Boss's Orders - Corbeau", "Ultra Rare | 255/217", 3000,
				"Ascended Heroes", "asc");
		TcgCollectorReferenceCatalogService.ReferenceCardMatch match =
				new TcgCollectorReferenceCatalogService.ReferenceCardMatch(42L, "Ascended Heroes", "Boss's Orders",
						"256", TcgCollectorReferenceCatalogService.MatchConfidence.UNIQUE_NAME);
		PokemonTcgCardCandidate first = candidate("asc-1", "Alpha", "1", "asc");
		PokemonTcgCardCandidate second = candidate("asc-2", "Beta", "2", "asc");
		when(mappingRepository.findById(3000L)).thenReturn(Optional.empty());
		when(referenceCatalogService.findCards(42L)).thenReturn(List.of(
				new TcgCollectorReferenceCatalogService.ReferenceCardIdentity("Alpha", "1"),
				new TcgCollectorReferenceCatalogService.ReferenceCardIdentity("Beta", "2")));
		when(pokemonTcgClient.searchSingleCard("name:\"Alpha\" number:1")).thenReturn(Optional.of(first));
		when(pokemonTcgClient.searchSingleCard("name:\"Beta\" number:2")).thenReturn(Optional.of(second));

		Optional<String> setId = new TcgReferencePokemonSetMappingService(
				mappingRepository, referenceCatalogService, pokemonTcgClient).resolve(card, match);

		assertThat(setId).contains("asc");
		ArgumentCaptor<TcgReferencePokemonSetMapping> saved = ArgumentCaptor.forClass(TcgReferencePokemonSetMapping.class);
		verify(mappingRepository).save(saved.capture());
		assertThat(saved.getValue().isMapped()).isTrue();
		assertThat(saved.getValue().getPokemonTcgSetId()).isEqualTo("asc");
	}

	@Test
	void continuesWithResolvedSetWhenTheMappingCacheCannotBeSaved() {
		CatalogCard card = new CatalogCard(255, "Boss's Orders - Corbeau", "Ultra Rare | 255/217", 3000,
				"Ascended Heroes", "asc");
		TcgCollectorReferenceCatalogService.ReferenceCardMatch match =
				new TcgCollectorReferenceCatalogService.ReferenceCardMatch(42L, "Ascended Heroes", "Boss's Orders",
						"256", TcgCollectorReferenceCatalogService.MatchConfidence.UNIQUE_NAME);
		when(mappingRepository.findById(3000L)).thenReturn(Optional.empty());
		when(referenceCatalogService.findCards(42L)).thenReturn(List.of(
				new TcgCollectorReferenceCatalogService.ReferenceCardIdentity("Alpha", "1"),
				new TcgCollectorReferenceCatalogService.ReferenceCardIdentity("Beta", "2")));
		when(pokemonTcgClient.searchSingleCard("name:\"Alpha\" number:1"))
				.thenReturn(Optional.of(candidate("asc-1", "Alpha", "1", "asc")));
		when(pokemonTcgClient.searchSingleCard("name:\"Beta\" number:2"))
				.thenReturn(Optional.of(candidate("asc-2", "Beta", "2", "asc")));
		when(mappingRepository.save(any(TcgReferencePokemonSetMapping.class)))
				.thenThrow(new DataIntegrityViolationException("constraint failure"));

		Optional<String> setId = new TcgReferencePokemonSetMappingService(
				mappingRepository, referenceCatalogService, pokemonTcgClient).resolve(card, match);

		assertThat(setId).contains("asc");
	}

	private static PokemonTcgCardCandidate candidate(String id, String name, String number, String setId) {
		return new PokemonTcgCardCandidate(id, name, number, setId, "Ascended Heroes", null, 217, 217, null,
				"https://images.test/s.png", "https://images.test/l.png");
	}
}
