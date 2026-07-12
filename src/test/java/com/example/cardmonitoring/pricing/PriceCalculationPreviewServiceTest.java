package com.example.cardmonitoring.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.cardmonitoring.catalog.CatalogCard;
import com.example.cardmonitoring.catalog.CatalogService;
import com.example.cardmonitoring.pokemontcg.CardImage;
import com.example.cardmonitoring.pokemontcg.CardImageService;

@ExtendWith(MockitoExtension.class)
class PriceCalculationPreviewServiceTest {

	@Mock
	private CatalogService catalogService;

	@Mock
	private PriceCalculationService priceCalculationService;

	@Mock
	private CardImageService cardImageService;

	@Test
	void calculatesPreviewWithoutAnyPersistenceDependency() {
		PriceCalculationRequest request = new PriceCalculationRequest(
				1472, 111151, "it", "Near Mint", false, false, false, null, null, false, false);
		PriceCriteria criteria = request.toPriceCriteria();
		CatalogCard card = new CatalogCard(
				111151, "Charizard", "Holo Rare | 4/102", 1472, "Base Set", "bs");
		PriceCalculationResult result = new PriceCalculationResult(
				"EUR",
				new BigDecimal("10100.00"),
				10_000L,
				10_200L,
				3,
				3,
				ConfidenceLevel.MEDIUM,
				List.of(new UsedMarketplaceOffer(418408517L, 10_000L, "EUR", 1, null, null, null)));
		when(catalogService.resolvePokemonCard(1472, 111151)).thenReturn(card);
		when(cardImageService.resolve(card)).thenReturn(java.util.Optional.of(new CardImage(
				"https://images.test/small.png", "https://images.test/large.png", "POKEMON_TCG_API")));
		when(priceCalculationService.calculate(criteria)).thenReturn(result);
		PriceCalculationPreviewService service = new PriceCalculationPreviewService(
				catalogService, priceCalculationService, cardImageService);

		PriceCalculationPreviewResponse preview = service.calculate(request);

		assertThat(preview.source()).isEqualTo("CARDTRADER_ACTIVE_LISTINGS");
		assertThat(preview.cardName()).isEqualTo("Charizard");
		assertThat(preview.imageUrlSmall()).isEqualTo("https://images.test/small.png");
		assertThat(preview.imageUrlLarge()).isEqualTo("https://images.test/large.png");
		assertThat(preview.averagePriceCents()).isEqualByComparingTo("10100.00");
		assertThat(preview.offersUsed()).containsExactly(
				new UsedMarketplaceOffer(418408517L, 10_000L, "EUR", 1, null, null, null));
		assertThat(preview.calculatedAt()).isNotNull();
		InOrder order = inOrder(catalogService, cardImageService, priceCalculationService);
		order.verify(catalogService).resolvePokemonCard(1472, 111151);
		order.verify(cardImageService).resolve(card);
		order.verify(priceCalculationService).calculate(criteria);
	}
}
