package com.example.cardmonitoring.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.example.cardmonitoring.cardtrader.CardTraderMarketplaceOffer;
import com.example.cardmonitoring.cardtrader.CardTraderProperties;
import com.example.cardmonitoring.cardtrader.PokemonCardProperties;

@Component
public class PriceCalculator {

	private static final int MAXIMUM_SAMPLE_SIZE = 4;
	private static final int AVERAGE_SCALE_IN_CENTS = 2;
	private static final Comparator<CardTraderMarketplaceOffer> PRICE_ORDER = Comparator
			.comparingLong((CardTraderMarketplaceOffer offer) -> offer.price().cents())
			.thenComparingLong(CardTraderMarketplaceOffer::id);

	private final CardTraderProperties cardTraderProperties;

	public PriceCalculator(CardTraderProperties cardTraderProperties) {
		this.cardTraderProperties = cardTraderProperties;
	}

	public PriceCalculationResult calculate(PriceCriteria criteria, List<CardTraderMarketplaceOffer> offers) {
		Objects.requireNonNull(criteria, "criteria is required");
		Objects.requireNonNull(offers, "offers are required");
		String expectedCurrency = cardTraderProperties.getExpectedCurrency();

		List<CardTraderMarketplaceOffer> compatibleOffers = offers.stream()
				.filter(Objects::nonNull)
				.filter(offer -> isCompatible(offer, criteria, expectedCurrency))
				.sorted(PRICE_ORDER)
				.toList();
		List<CardTraderMarketplaceOffer> usedOffers = compatibleOffers.stream()
				.limit(MAXIMUM_SAMPLE_SIZE)
				.toList();

		if (usedOffers.isEmpty()) {
			return new PriceCalculationResult(expectedCurrency, null, null, null,
					compatibleOffers.size(), 0, ConfidenceLevel.NO_DATA, List.of());
		}

		BigDecimal totalPriceCents = usedOffers.stream()
				.map(offer -> BigDecimal.valueOf(offer.price().cents()))
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal averagePriceCents = totalPriceCents.divide(
				BigDecimal.valueOf(usedOffers.size()), AVERAGE_SCALE_IN_CENTS, RoundingMode.HALF_UP);

		return new PriceCalculationResult(
				expectedCurrency,
				averagePriceCents,
				usedOffers.get(0).price().cents(),
				usedOffers.get(usedOffers.size() - 1).price().cents(),
				compatibleOffers.size(),
				usedOffers.size(),
				confidenceFor(usedOffers.size()),
				usedOffers.stream()
						.map(offer -> {
							GradingDetails grading = criteria.graded()
									? GradingDescriptionParser.parse(offer.description()).orElse(null)
									: null;
							return new UsedMarketplaceOffer(
									offer.id(),
									offer.price().cents(),
									offer.price().currency(),
									offer.quantity(),
									criteria.graded() ? offer.description() : null,
									grading == null ? null : grading.company(),
									grading == null ? null : grading.grade());
						})
						.toList());
	}

	private static boolean isCompatible(CardTraderMarketplaceOffer offer, PriceCriteria criteria,
			String expectedCurrency) {
		if (offer.blueprintId() != criteria.blueprintId()
				|| offer.expansion() == null
				|| offer.expansion().id() != criteria.expansionId()
				|| offer.quantity() <= 0
				|| offer.onVacation()
				|| offer.price() == null
				|| offer.price().cents() < 0
				|| !expectedCurrency.equals(offer.price().currency())) {
			return false;
		}

		if (!isGradingCompatible(offer, criteria)) {
			return false;
		}

		PokemonCardProperties properties = offer.properties();
		return properties != null
				&& criteria.language().equals(properties.language())
				&& (criteria.condition() == null || criteria.condition().equals(properties.condition()))
				&& Boolean.valueOf(criteria.firstEdition()).equals(properties.firstEdition())
				&& Boolean.valueOf(criteria.reverse()).equals(properties.reverse())
				&& Boolean.valueOf(criteria.signed()).equals(properties.signed())
				&& Boolean.valueOf(criteria.altered()).equals(properties.altered());
	}

	private static boolean isGradingCompatible(CardTraderMarketplaceOffer offer, PriceCriteria criteria) {
		if (offer.graded() != criteria.graded()) {
			return false;
		}
		if (!criteria.graded()) {
			return true;
		}
		if (criteria.gradingCompany() == null && criteria.gradingGrade() == null) {
			return true;
		}
		GradingDetails grading = GradingDescriptionParser.parse(offer.description()).orElse(null);
		return grading != null
				&& criteria.gradingCompany().equals(grading.company())
				&& criteria.gradingGrade().equals(grading.grade());
	}

	private static ConfidenceLevel confidenceFor(int usedOffers) {
		if (usedOffers >= MAXIMUM_SAMPLE_SIZE) {
			return ConfidenceLevel.HIGH;
		}
		if (usedOffers >= 2) {
			return ConfidenceLevel.MEDIUM;
		}
		return ConfidenceLevel.LOW;
	}
}
