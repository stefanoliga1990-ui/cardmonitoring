package com.example.cardmonitoring.pricing;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.cardmonitoring.cardtrader.CardTraderClient;
import com.example.cardmonitoring.cardtrader.CardTraderMarketplaceOffer;

@Service
public class PriceCalculationService {

	private final CardTraderClient cardTraderClient;
	private final PriceCalculator priceCalculator;

	public PriceCalculationService(CardTraderClient cardTraderClient, PriceCalculator priceCalculator) {
		this.cardTraderClient = cardTraderClient;
		this.priceCalculator = priceCalculator;
	}

	public PriceCalculationResult calculate(PriceCriteria criteria) {
		List<CardTraderMarketplaceOffer> offers = cardTraderClient.getMarketplaceProducts(
				criteria.blueprintId(), criteria.language());
		return priceCalculator.calculate(criteria, offers);
	}
}
