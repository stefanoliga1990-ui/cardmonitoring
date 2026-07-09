package com.example.cardmonitoring.pricing;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.cardmonitoring.catalog.CatalogCard;
import com.example.cardmonitoring.catalog.CatalogService;
import com.example.cardmonitoring.pokemontcg.CardImage;
import com.example.cardmonitoring.pokemontcg.CardImageService;

@Service
public class PriceCalculationPreviewService {

	private static final Logger LOGGER = LoggerFactory.getLogger(PriceCalculationPreviewService.class);

	private final CatalogService catalogService;
	private final PriceCalculationService priceCalculationService;
	private final CardImageService cardImageService;

	public PriceCalculationPreviewService(
			CatalogService catalogService,
			PriceCalculationService priceCalculationService,
			CardImageService cardImageService) {
		this.catalogService = catalogService;
		this.priceCalculationService = priceCalculationService;
		this.cardImageService = cardImageService;
	}

	public PriceCalculationPreviewResponse calculate(PriceCalculationRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("request is required");
		}
		PriceCriteria criteria = request.toPriceCriteria();
		LOGGER.info(
				"Starting price preview calculation: blueprintId={}, expansionId={}, language={}, condition={}, firstEdition={}, reverse={}, graded={}, signed={}, altered={}",
				criteria.blueprintId(), criteria.expansionId(), criteria.language(), criteria.condition(),
				criteria.firstEdition(), criteria.reverse(), criteria.graded(), criteria.signed(), criteria.altered());
		CatalogCard card = catalogService.resolvePokemonCard(criteria.expansionId(), criteria.blueprintId());
		CardImage image = cardImageService.resolve(card).orElse(null);
		LOGGER.info("Price preview image lookup completed: blueprintId={}, imageFound={}, imageSource={}",
				criteria.blueprintId(), image != null, image == null ? null : image.source());
		PriceCalculationResult result = priceCalculationService.calculate(criteria);
		LOGGER.info(
				"Price preview calculation completed: blueprintId={}, hasPrice={}, compatibleOffers={}, usedOffers={}, confidence={}",
				criteria.blueprintId(), result.hasPrice(), result.compatibleOffers(), result.usedOffers(),
				result.confidence());
		return PriceCalculationPreviewResponse.from(Instant.now(), card, image, criteria, result);
	}
}
