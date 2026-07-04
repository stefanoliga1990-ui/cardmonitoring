package com.example.cardmonitoring.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.cardmonitoring.pricing.ConfidenceLevel;
import com.example.cardmonitoring.pricing.PriceCalculationResult;
import com.example.cardmonitoring.pricing.PriceCriteria;
import com.example.cardmonitoring.user.AppUser;
import com.example.cardmonitoring.user.AppUserRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MonitoringPersistenceTest {

	private static final PriceCriteria CRITERIA = new PriceCriteria(
			111151, 1472, "it", "Near Mint", false, false, false, false, false);

	@Autowired
	private MonitoringRepository monitoringRepository;

	@Autowired
	private PriceObservationRepository observationRepository;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void persistsMonitoringAndAggregateObservation() {
		Instant checkedAt = Instant.parse("2026-07-03T08:00:00Z");
		Monitoring monitoring = new Monitoring(
				createUser("ash"), "Charizard", "Holo Rare | 4/102", "Base Set", "bs", CRITERIA, "EUR");
		monitoring.recordSuccessfulCheck(checkedAt);
		monitoring = monitoringRepository.saveAndFlush(monitoring);

		PriceCalculationResult result = new PriceCalculationResult(
				"EUR", new BigDecimal("10100.67"), 10_000L, 10_201L, 7, 5, ConfidenceLevel.HIGH);
		Instant observedAt = Instant.parse("2026-07-03T08:00:01Z");
		observationRepository.saveAndFlush(new PriceObservation(monitoring, observedAt, result));

		Long monitoringId = monitoring.getId();
		entityManager.clear();

		Monitoring reloaded = monitoringRepository.findById(monitoringId).orElseThrow();
		assertThat(reloaded.getBlueprintId()).isEqualTo(111151);
		assertThat(reloaded.getOwner().getUsername()).isEqualTo("ash");
		assertThat(reloaded.getExpansionId()).isEqualTo(1472);
		assertThat(reloaded.getCardName()).isEqualTo("Charizard");
		assertThat(reloaded.getCardVersion()).isEqualTo("Holo Rare | 4/102");
		assertThat(reloaded.getLanguage()).isEqualTo("it");
		assertThat(reloaded.getCondition()).isEqualTo("Near Mint");
		assertThat(reloaded.isFirstEdition()).isFalse();
		assertThat(reloaded.isReverse()).isFalse();
		assertThat(reloaded.isGraded()).isFalse();
		assertThat(reloaded.isSigned()).isFalse();
		assertThat(reloaded.isAltered()).isFalse();
		assertThat(reloaded.isActive()).isTrue();
		assertThat(reloaded.getCurrency()).isEqualTo("EUR");
		assertThat(reloaded.getLastCheckedAt()).isEqualTo(checkedAt);
		assertThat(reloaded.getLastError()).isNull();

		List<PriceObservation> observations = observationRepository
				.findByMonitoringIdOrderByObservedAtAsc(monitoringId);
		assertThat(observations).singleElement().satisfies(observation -> {
			assertThat(observation.getObservedAt()).isEqualTo(observedAt);
			assertThat(observation.getCurrency()).isEqualTo("EUR");
			assertThat(observation.getAveragePriceCents()).isEqualByComparingTo("10100.67");
			assertThat(observation.getMinimumPriceCents()).isEqualTo(10_000L);
			assertThat(observation.getMaximumPriceCents()).isEqualTo(10_201L);
			assertThat(observation.getCompatibleOffers()).isEqualTo(7);
			assertThat(observation.getUsedOffers()).isEqualTo(5);
			assertThat(observation.getConfidence()).isEqualTo(ConfidenceLevel.HIGH);
		});
	}

	@Test
	void persistsNoDataObservationWithoutPrices() {
		Monitoring monitoring = monitoringRepository.saveAndFlush(new Monitoring(
				createUser("misty"), "Charizard", "Holo Rare | 4/102", "Base Set", "bs", CRITERIA, "EUR"));
		PriceCalculationResult result = new PriceCalculationResult(
				"EUR", null, null, null, 0, 0, ConfidenceLevel.NO_DATA);

		observationRepository.saveAndFlush(new PriceObservation(
				monitoring, Instant.parse("2026-07-03T09:00:00Z"), result));
		Long monitoringId = monitoring.getId();
		entityManager.clear();

		PriceObservation observation = observationRepository
				.findByMonitoringIdOrderByObservedAtAsc(monitoringId).get(0);
		assertThat(observation.getAveragePriceCents()).isNull();
		assertThat(observation.getMinimumPriceCents()).isNull();
		assertThat(observation.getMaximumPriceCents()).isNull();
		assertThat(observation.getConfidence()).isEqualTo(ConfidenceLevel.NO_DATA);
	}

	@Test
	void persistsInactiveStateAndSafeLengthLastError() {
		Monitoring monitoring = new Monitoring(
				createUser("brock"), "Charizard", "Holo Rare | 4/102", "Base Set", "bs", CRITERIA, "EUR");
		Instant checkedAt = Instant.parse("2026-07-03T10:00:00Z");
		monitoring.recordFailedCheck(checkedAt, "x".repeat(1200));
		monitoring.deactivate();
		Long monitoringId = monitoringRepository.saveAndFlush(monitoring).getId();
		entityManager.clear();

		Monitoring reloaded = monitoringRepository.findById(monitoringId).orElseThrow();
		assertThat(reloaded.isActive()).isFalse();
		assertThat(reloaded.getLastCheckedAt()).isEqualTo(checkedAt);
		assertThat(reloaded.getLastError()).hasSize(1000);
		assertThat(monitoringRepository.findByActiveTrue()).doesNotContain(reloaded);
	}

	private AppUser createUser(String username) {
		return appUserRepository.saveAndFlush(new AppUser(username, "$2a$10$testhash"));
	}
}
