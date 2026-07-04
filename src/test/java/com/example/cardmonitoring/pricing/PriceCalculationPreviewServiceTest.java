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

@ExtendWith(MockitoExtension.class)
class PriceCalculationPreviewServiceTest {

	@Mock
	private CatalogService catalogService;

	@Mock
	private PriceCalculationService priceCalculationService;

	@Test
	void calculatesPreviewWithoutAnyPersistenceDependency() {
		PriceCalculationRequest request = new PriceCalculationRequest(
				1472, 111151, "it", "Near Mint", false, false, false, false, false);
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
				List.of(new UsedMarketplaceOffer(418408517L, 10_000L, "EUR", 1)));
		when(catalogService.resolvePokemonCard(1472, 111151)).thenReturn(card);
		when(priceCalculationService.calculate(criteria)).thenReturn(result);
		PriceCalculationPreviewService service = new PriceCalculationPreviewService(
				catalogService, priceCalculationService);

		PriceCalculationPreviewResponse preview = service.calculate(request);

		assertThat(preview.source()).isEqualTo("CARDTRADER_ACTIVE_LISTINGS");
		assertThat(preview.cardName()).isEqualTo("Charizard");
		assertThat(preview.averagePriceCents()).isEqualByComparingTo("10100.00");
		assertThat(preview.offersUsed()).containsExactly(
				new UsedMarketplaceOffer(418408517L, 10_000L, "EUR", 1));
		assertThat(preview.calculatedAt()).isNotNull();
		InOrder order = inOrder(catalogService, priceCalculationService);
		order.verify(catalogService).resolvePokemonCard(1472, 111151);
		order.verify(priceCalculationService).calculate(criteria);
	}
}
