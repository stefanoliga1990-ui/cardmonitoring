package com.example.cardmonitoring.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.example.cardmonitoring.catalog.CatalogCard;

public record PriceCalculationPreviewResponse(
		Instant calculatedAt,
		String source,
		long expansionId,
		String expansionName,
		String expansionCode,
		long blueprintId,
		String cardName,
		String cardVersion,
		String language,
		String condition,
		boolean firstEdition,
		boolean reverse,
		boolean graded,
		boolean signed,
		boolean altered,
		String currency,
		BigDecimal averagePriceCents,
		Long minimumPriceCents,
		Long maximumPriceCents,
		int compatibleOffers,
		int usedOffers,
		ConfidenceLevel confidence,
		List<UsedMarketplaceOffer> offersUsed) {

	private static final String CARDTRADER_ACTIVE_LISTINGS = "CARDTRADER_ACTIVE_LISTINGS";

	static PriceCalculationPreviewResponse from(
			Instant calculatedAt,
			CatalogCard card,
			PriceCriteria criteria,
			PriceCalculationResult result) {
		return new PriceCalculationPreviewResponse(
				calculatedAt,
				CARDTRADER_ACTIVE_LISTINGS,
				card.expansionId(),
				card.expansionName(),
				card.expansionCode(),
				card.blueprintId(),
				card.cardName(),
				card.cardVersion(),
				criteria.language(),
				criteria.condition(),
				criteria.firstEdition(),
				criteria.reverse(),
				criteria.graded(),
				criteria.signed(),
				criteria.altered(),
				result.currency(),
				result.averagePriceCents(),
				result.minimumPriceCents(),
				result.maximumPriceCents(),
				result.compatibleOffers(),
				result.usedOffers(),
				result.confidence(),
				result.offersUsed());
	}
}
