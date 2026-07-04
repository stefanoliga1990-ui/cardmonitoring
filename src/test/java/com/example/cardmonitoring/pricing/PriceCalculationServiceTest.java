package com.example.cardmonitoring.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.cardmonitoring.cardtrader.CardTraderClient;
import com.example.cardmonitoring.cardtrader.CardTraderMarketplaceOffer;

@ExtendWith(MockitoExtension.class)
class PriceCalculationServiceTest {

	@Mock
	private CardTraderClient cardTraderClient;

	@Mock
	private PriceCalculator priceCalculator;

	@InjectMocks
	private PriceCalculationService service;

	@Test
	void loadsMarketplaceOffersUsingCriteriaAndDelegatesCalculation() {
		PriceCriteria criteria = new PriceCriteria(
				111151, 1472, "it", "Near Mint", false, false, false, false, false);
		List<CardTraderMarketplaceOffer> offers = List.of();
		PriceCalculationResult expectedResult = new PriceCalculationResult(
				"EUR", null, null, null, 0, 0, ConfidenceLevel.NO_DATA);
		when(cardTraderClient.getMarketplaceProducts(111151, "it")).thenReturn(offers);
		when(priceCalculator.calculate(criteria, offers)).thenReturn(expectedResult);

		assertThat(service.calculate(criteria)).isSameAs(expectedResult);
		verify(cardTraderClient).getMarketplaceProducts(111151, "it");
		verify(priceCalculator).calculate(criteria, offers);
	}
}
