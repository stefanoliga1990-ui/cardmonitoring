package com.example.cardmonitoring.pricing;

public record UsedMarketplaceOffer(
		long offerId,
		long priceCents,
		String currency,
		int quantity,
		String description,
		String gradingCompany,
		String gradingGrade) {
}
