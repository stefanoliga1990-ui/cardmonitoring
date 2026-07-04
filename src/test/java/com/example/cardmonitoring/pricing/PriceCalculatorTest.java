package com.example.cardmonitoring.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.example.cardmonitoring.cardtrader.CardTraderExpansionSummary;
import com.example.cardmonitoring.cardtrader.CardTraderMarketplaceOffer;
import com.example.cardmonitoring.cardtrader.CardTraderMoney;
import com.example.cardmonitoring.cardtrader.CardTraderProperties;
import com.example.cardmonitoring.cardtrader.PokemonCardProperties;

class PriceCalculatorTest {

	private static final PriceCriteria CRITERIA = new PriceCriteria(
			111151, 1472, "it", "Near Mint", false, false, false, false, false);

	private PriceCalculator calculator;

	@BeforeEach
	void setUp() {
		CardTraderProperties properties = new CardTraderProperties();
		properties.setExpectedCurrency("EUR");
		calculator = new PriceCalculator(properties);
	}

	@Test
	void calculatesAverageMinimumMaximumAndCounts() {
		PriceCalculationResult result = calculator.calculate(CRITERIA, List.of(
				offer(1, 10_000).build(),
				offer(2, 10_100).build(),
				offer(3, 10_200).build()));

		assertThat(result.currency()).isEqualTo("EUR");
		assertThat(result.averagePriceCents()).isEqualByComparingTo("10100.00");
		assertThat(result.minimumPriceCents()).isEqualTo(10_000);
		assertThat(result.maximumPriceCents()).isEqualTo(10_200);
		assertThat(result.compatibleOffers()).isEqualTo(3);
		assertThat(result.usedOffers()).isEqualTo(3);
		assertThat(result.confidence()).isEqualTo(ConfidenceLevel.MEDIUM);
		assertThat(result.hasPrice()).isTrue();
	}

	@Test
	void sortsOffersAndUsesOnlyFiveCheapest() {
		PriceCalculationResult result = calculator.calculate(CRITERIA, List.of(
				offer(1, 500).build(),
				offer(2, 100).build(),
				offer(3, 300).build(),
				offer(4, 200).build(),
				offer(5, 400).build(),
				offer(6, 50).build()));

		assertThat(result.compatibleOffers()).isEqualTo(6);
		assertThat(result.usedOffers()).isEqualTo(5);
		assertThat(result.minimumPriceCents()).isEqualTo(50);
		assertThat(result.maximumPriceCents()).isEqualTo(400);
		assertThat(result.averagePriceCents()).isEqualByComparingTo("210.00");
		assertThat(result.confidence()).isEqualTo(ConfidenceLevel.HIGH);
		assertThat(result.offersUsed())
				.extracting(UsedMarketplaceOffer::offerId)
				.containsExactly(6L, 2L, 4L, 3L, 5L);
		assertThat(result.offersUsed())
				.extracting(UsedMarketplaceOffer::priceCents)
				.containsExactly(50L, 100L, 200L, 300L, 400L);
	}

	@Test
	void calculatesFractionalCentAverageWithoutDouble() {
		PriceCalculationResult result = calculator.calculate(CRITERIA, List.of(
				offer(1, 100).build(),
				offer(2, 101).build(),
				offer(3, 101).build()));

		assertThat(result.averagePriceCents()).isEqualByComparingTo(new BigDecimal("100.67"));
	}

	@Test
	void returnsExplicitNoDataResult() {
		PriceCalculationResult result = calculator.calculate(CRITERIA, List.of());

		assertThat(result.currency()).isEqualTo("EUR");
		assertThat(result.averagePriceCents()).isNull();
		assertThat(result.minimumPriceCents()).isNull();
		assertThat(result.maximumPriceCents()).isNull();
		assertThat(result.compatibleOffers()).isZero();
		assertThat(result.usedOffers()).isZero();
		assertThat(result.confidence()).isEqualTo(ConfidenceLevel.NO_DATA);
		assertThat(result.hasPrice()).isFalse();
		assertThat(result.offersUsed()).isEmpty();
	}

	@ParameterizedTest(name = "excludes {0}")
	@MethodSource("incompatibleOffers")
	void excludesEveryIncompatibleOffer(String reason, CardTraderMarketplaceOffer offer) {
		PriceCalculationResult result = calculator.calculate(CRITERIA, List.of(offer));

		assertThat(result.confidence()).as(reason).isEqualTo(ConfidenceLevel.NO_DATA);
		assertThat(result.compatibleOffers()).as(reason).isZero();
	}

	@ParameterizedTest
	@MethodSource("confidenceCases")
	void assignsConfidenceFromUsedSampleSize(int offerCount, ConfidenceLevel expectedConfidence) {
		List<CardTraderMarketplaceOffer> offers = IntStream.range(0, offerCount)
				.mapToObj(index -> offer(index + 1L, 1_000 + index).build())
				.toList();

		assertThat(calculator.calculate(CRITERIA, offers).confidence()).isEqualTo(expectedConfidence);
	}

	private static Stream<Arguments> incompatibleOffers() {
		PokemonCardProperties missingFirstEdition = new PokemonCardProperties(
				"Near Mint", false, false, "004", null, "Holo Rare", "it",
				"Energy Burn | Fire Spin", true, false);
		return Stream.of(
				Arguments.of("wrong blueprint", offer(1, 100).blueprintId(999).build()),
				Arguments.of("wrong expansion including Shadowless", offer(1, 100).expansionId(1969).build()),
				Arguments.of("unavailable quantity", offer(1, 100).quantity(0).build()),
				Arguments.of("seller on vacation", offer(1, 100).onVacation(true).build()),
				Arguments.of("different currency", offer(1, 100).currency("USD").build()),
				Arguments.of("different language", offer(1, 100).language("en").build()),
				Arguments.of("different condition", offer(1, 100).condition("Poor").build()),
				Arguments.of("different first edition", offer(1, 100).firstEdition(true).build()),
				Arguments.of("different reverse", offer(1, 100).reverse(true).build()),
				Arguments.of("different graded", offer(1, 100).graded(true).build()),
				Arguments.of("different signed", offer(1, 100).signed(true).build()),
				Arguments.of("different altered", offer(1, 100).altered(true).build()),
				Arguments.of("missing filter property", offer(1, 100).properties(missingFirstEdition).build()),
				Arguments.of("missing properties object", offer(1, 100).properties(null).build()));
	}

	private static Stream<Arguments> confidenceCases() {
		return Stream.of(
				Arguments.of(1, ConfidenceLevel.LOW),
				Arguments.of(2, ConfidenceLevel.LOW),
				Arguments.of(3, ConfidenceLevel.MEDIUM),
				Arguments.of(4, ConfidenceLevel.MEDIUM),
				Arguments.of(5, ConfidenceLevel.HIGH));
	}

	private static OfferBuilder offer(long id, long priceCents) {
		return new OfferBuilder(id, priceCents);
	}

	private static final class OfferBuilder {

		private final long id;
		private final long priceCents;
		private long blueprintId = 111151;
		private long expansionId = 1472;
		private String currency = "EUR";
		private int quantity = 1;
		private String language = "it";
		private String condition = "Near Mint";
		private boolean firstEdition;
		private boolean reverse;
		private boolean graded;
		private boolean signed;
		private boolean altered;
		private boolean onVacation;
		private PokemonCardProperties properties;
		private boolean propertiesOverridden;

		private OfferBuilder(long id, long priceCents) {
			this.id = id;
			this.priceCents = priceCents;
		}

		OfferBuilder blueprintId(long value) { blueprintId = value; return this; }
		OfferBuilder expansionId(long value) { expansionId = value; return this; }
		OfferBuilder currency(String value) { currency = value; return this; }
		OfferBuilder quantity(int value) { quantity = value; return this; }
		OfferBuilder language(String value) { language = value; return this; }
		OfferBuilder condition(String value) { condition = value; return this; }
		OfferBuilder firstEdition(boolean value) { firstEdition = value; return this; }
		OfferBuilder reverse(boolean value) { reverse = value; return this; }
		OfferBuilder graded(boolean value) { graded = value; return this; }
		OfferBuilder signed(boolean value) { signed = value; return this; }
		OfferBuilder altered(boolean value) { altered = value; return this; }
		OfferBuilder onVacation(boolean value) { onVacation = value; return this; }
		OfferBuilder properties(PokemonCardProperties value) {
			properties = value;
			propertiesOverridden = true;
			return this;
		}

		CardTraderMarketplaceOffer build() {
			PokemonCardProperties effectiveProperties = propertiesOverridden
					? properties
					: new PokemonCardProperties(condition, signed, altered, "004", firstEdition,
							"Holo Rare", language, "Energy Burn | Fire Spin", true, reverse);
			return new CardTraderMarketplaceOffer(
					id,
					blueprintId,
					"Charizard",
					new CardTraderExpansionSummary(expansionId, "bs", "Base Set"),
					new CardTraderMoney(priceCents, currency),
					quantity,
					effectiveProperties,
					graded,
					onVacation);
		}
	}
}
