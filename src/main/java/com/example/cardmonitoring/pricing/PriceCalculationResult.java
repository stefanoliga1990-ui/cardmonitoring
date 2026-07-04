package com.example.cardmonitoring.pricing;

import java.math.BigDecimal;
import java.util.List;

public record PriceCalculationResult(
		String currency,
		BigDecimal averagePriceCents,
		Long minimumPriceCents,
		Long maximumPriceCents,
		int compatibleOffers,
		int usedOffers,
		ConfidenceLevel confidence,
		List<UsedMarketplaceOffer> offersUsed) {

	public PriceCalculationResult {
		offersUsed = offersUsed == null ? List.of() : List.copyOf(offersUsed);
	}

	public PriceCalculationResult(
			String currency,
			BigDecimal averagePriceCents,
			Long minimumPriceCents,
			Long maximumPriceCents,
			int compatibleOffers,
			int usedOffers,
			ConfidenceLevel confidence) {
		this(currency, averagePriceCents, minimumPriceCents, maximumPriceCents,
				compatibleOffers, usedOffers, confidence, List.of());
	}

	public boolean hasPrice() {
		return averagePriceCents != null;
	}
}
