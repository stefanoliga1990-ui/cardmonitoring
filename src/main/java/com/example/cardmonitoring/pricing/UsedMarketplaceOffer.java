package com.example.cardmonitoring.pricing;

public record UsedMarketplaceOffer(
		long offerId,
		long priceCents,
		String currency,
		int quantity) {
}
