package com.example.cardmonitoring.pricing;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.example.cardmonitoring.catalog.CatalogCard;
import com.example.cardmonitoring.catalog.CatalogService;

@Service
public class PriceCalculationPreviewService {

	private final CatalogService catalogService;
	private final PriceCalculationService priceCalculationService;

	public PriceCalculationPreviewService(
			CatalogService catalogService,
			PriceCalculationService priceCalculationService) {
		this.catalogService = catalogService;
		this.priceCalculationService = priceCalculationService;
	}

	public PriceCalculationPreviewResponse calculate(PriceCalculationRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("request is required");
		}
		PriceCriteria criteria = request.toPriceCriteria();
		CatalogCard card = catalogService.resolvePokemonCard(criteria.expansionId(), criteria.blueprintId());
		PriceCalculationResult result = priceCalculationService.calculate(criteria);
		return PriceCalculationPreviewResponse.from(Instant.now(), card, criteria, result);
	}
}
