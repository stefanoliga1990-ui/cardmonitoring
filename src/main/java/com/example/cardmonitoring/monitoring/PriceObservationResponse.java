package com.example.cardmonitoring.monitoring;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.cardmonitoring.pricing.ConfidenceLevel;

public record PriceObservationResponse(
		long id,
		Instant observedAt,
		String currency,
		BigDecimal averagePriceCents,
		Long minimumPriceCents,
		Long maximumPriceCents,
		int compatibleOffers,
		int usedOffers,
		ConfidenceLevel confidence) {

	static PriceObservationResponse from(PriceObservation observation) {
		return new PriceObservationResponse(
				observation.getId(),
				observation.getObservedAt(),
				observation.getCurrency(),
				observation.getAveragePriceCents(),
				observation.getMinimumPriceCents(),
				observation.getMaximumPriceCents(),
				observation.getCompatibleOffers(),
				observation.getUsedOffers(),
				observation.getConfidence());
	}
}
