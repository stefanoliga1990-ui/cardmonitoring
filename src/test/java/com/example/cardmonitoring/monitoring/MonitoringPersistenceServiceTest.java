package com.example.cardmonitoring.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.cardmonitoring.monitoring.MonitoringPersistenceService.CardIdentity;
import com.example.cardmonitoring.pricing.ConfidenceLevel;
import com.example.cardmonitoring.pricing.PriceCalculationResult;
import com.example.cardmonitoring.pricing.PriceCriteria;
import com.example.cardmonitoring.user.AppUser;
import com.example.cardmonitoring.user.AppUserRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MonitoringPersistenceServiceTest {

	private static final PriceCriteria CRITERIA = new PriceCriteria(
			111151, 1472, "it", "Near Mint", false, false, false, null, null, false, false);
	private static final CardIdentity CARD = new CardIdentity(
			"Charizard", "Holo Rare | 4/102", "Base Set", "bs", null, null, null);

	@Autowired
	private MonitoringPersistenceService service;

	@Autowired
	private AppUserRepository appUserRepository;

	@Test
	void supportsCompleteMonitoringLifecycleWithoutDeletingHistory() {
		long ownerId = createUser("stefano");
		Instant initialCheck = Instant.parse("2026-07-03T08:00:00Z");
		CreatedMonitoringResponse created = service.createWithInitialObservation(
				ownerId, CARD, CRITERIA, "EUR", initialCheck, result("10100.00", ConfidenceLevel.MEDIUM));
		long monitoringId = created.monitoring().id();

		assertThat(created.monitoring().active()).isTrue();
		assertThat(created.monitoring().purchasePriceCents()).isNull();
		assertThat(created.monitoring().lastCheckedAt()).isEqualTo(initialCheck);
		assertThat(created.initialObservation().averagePriceCents()).isEqualByComparingTo("10100.00");
		assertThat(service.findActive(ownerId)).extracting(MonitoringResponse::id).containsExactly(monitoringId);
		assertThat(service.findRefreshTarget(ownerId, monitoringId).criteria()).isEqualTo(CRITERIA);
		assertThat(service.updatePurchasePrice(ownerId, monitoringId, 12_345L).purchasePriceCents()).isEqualTo(12_345L);
		assertThat(service.findById(ownerId, monitoringId).purchasePriceCents()).isEqualTo(12_345L);
		assertThat(service.updatePurchasePrice(ownerId, monitoringId, null).purchasePriceCents()).isNull();
		assertThat(service.findById(ownerId, monitoringId).purchasePriceCents()).isNull();
		assertThatThrownBy(() -> service.updatePurchasePrice(ownerId, monitoringId, 0L))
				.isInstanceOf(IllegalArgumentException.class);

		Instant secondCheck = Instant.parse("2026-07-03T09:00:00Z");
		service.saveObservation(ownerId, monitoringId, secondCheck, result("10200.00", ConfidenceLevel.HIGH));
		assertThat(service.findObservations(ownerId, monitoringId))
				.extracting(PriceObservationResponse::averagePriceCents)
				.containsExactly(new BigDecimal("10100.00"), new BigDecimal("10200.00"));

		service.deactivate(ownerId, monitoringId);
		assertThat(service.findActive(ownerId)).isEmpty();
		assertThat(service.findById(ownerId, monitoringId).active()).isFalse();
		assertThat(service.findObservations(ownerId, monitoringId)).hasSize(2);
		assertThatThrownBy(() -> service.findRefreshTarget(ownerId, monitoringId))
				.isInstanceOf(MonitoringInactiveException.class);
	}

	@Test
	void reportsMissingMonitoringUniformlyFromEveryPersistenceOperation() {
		long ownerId = createUser("mario");
		assertThatThrownBy(() -> service.findById(ownerId, 999L))
				.isInstanceOf(MonitoringNotFoundException.class);
		assertThatThrownBy(() -> service.findObservations(ownerId, 999L))
				.isInstanceOf(MonitoringNotFoundException.class);
		assertThatThrownBy(() -> service.deactivate(ownerId, 999L))
				.isInstanceOf(MonitoringNotFoundException.class);
	}

	@Test
	void neverExposesAnotherUsersMonitoring() {
		long ownerId = createUser("owner");
		long otherUserId = createUser("other");
		CreatedMonitoringResponse created = service.createWithInitialObservation(
				ownerId,
				CARD,
				CRITERIA,
				"EUR",
				Instant.parse("2026-07-03T08:00:00Z"),
				result("10100.00", ConfidenceLevel.MEDIUM));
		long monitoringId = created.monitoring().id();

		assertThat(service.findActive(otherUserId)).isEmpty();
		assertThatThrownBy(() -> service.findById(otherUserId, monitoringId))
				.isInstanceOf(MonitoringNotFoundException.class);
		assertThatThrownBy(() -> service.findObservations(otherUserId, monitoringId))
				.isInstanceOf(MonitoringNotFoundException.class);
		assertThatThrownBy(() -> service.deactivate(otherUserId, monitoringId))
				.isInstanceOf(MonitoringNotFoundException.class);
		assertThat(service.findById(ownerId, monitoringId).active()).isTrue();
	}

	private long createUser(String username) {
		return appUserRepository.saveAndFlush(new AppUser(username, "$2a$10$testhash")).getId();
	}

	private static PriceCalculationResult result(String average, ConfidenceLevel confidence) {
		int offers = confidence == ConfidenceLevel.HIGH ? 4 : 3;
		return new PriceCalculationResult(
				"EUR", new BigDecimal(average), 10_000L, 10_500L, offers, offers, confidence);
	}
}
