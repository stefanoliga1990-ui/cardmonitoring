package com.example.cardmonitoring.monitoring;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cardmonitoring.pricing.PriceCalculationResult;
import com.example.cardmonitoring.pricing.PriceCriteria;
import com.example.cardmonitoring.user.AppUser;
import com.example.cardmonitoring.user.AppUserRepository;

@Service
public class MonitoringPersistenceService {

	private final MonitoringRepository monitoringRepository;
	private final PriceObservationRepository observationRepository;
	private final AppUserRepository appUserRepository;

	public MonitoringPersistenceService(MonitoringRepository monitoringRepository,
			PriceObservationRepository observationRepository,
			AppUserRepository appUserRepository) {
		this.monitoringRepository = monitoringRepository;
		this.observationRepository = observationRepository;
		this.appUserRepository = appUserRepository;
	}

	@Transactional
	public CreatedMonitoringResponse createWithInitialObservation(
			long ownerId,
			CardIdentity card,
			PriceCriteria criteria,
			String currency,
			Instant observedAt,
			PriceCalculationResult result) {
		AppUser owner = appUserRepository.findById(ownerId)
				.orElseThrow(() -> new IllegalStateException("Authenticated user no longer exists"));
		Monitoring monitoring = new Monitoring(
				owner,
				card.cardName(),
				card.cardVersion(),
				card.expansionName(),
				card.expansionCode(),
				criteria,
				currency);
		monitoring.recordSuccessfulCheck(observedAt);
		monitoringRepository.save(monitoring);

		PriceObservation observation = new PriceObservation(monitoring, observedAt, result);
		observationRepository.save(observation);
		return new CreatedMonitoringResponse(
				MonitoringResponse.from(monitoring),
				PriceObservationResponse.from(observation));
	}

	@Transactional(readOnly = true)
	public List<MonitoringResponse> findActive(long ownerId) {
		return monitoringRepository.findByOwnerIdAndActiveTrueOrderByCreatedAtDesc(ownerId).stream()
				.map(MonitoringResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public MonitoringResponse findById(long ownerId, long monitoringId) {
		return MonitoringResponse.from(requireOwnedMonitoring(ownerId, monitoringId));
	}

	@Transactional(readOnly = true)
	public List<Long> findActiveIdsForScheduler() {
		return monitoringRepository.findByActiveTrueOrderByIdAsc().stream()
				.map(Monitoring::getId)
				.toList();
	}

	@Transactional(readOnly = true)
	public RefreshTarget findRefreshTarget(long ownerId, long monitoringId) {
		return refreshTarget(requireOwnedMonitoring(ownerId, monitoringId));
	}

	@Transactional(readOnly = true)
	public RefreshTarget findRefreshTargetForScheduler(long monitoringId) {
		return refreshTarget(requireMonitoring(monitoringId));
	}

	private RefreshTarget refreshTarget(Monitoring monitoring) {
		if (!monitoring.isActive()) {
			throw new MonitoringInactiveException(monitoring.getId());
		}
		return new RefreshTarget(monitoring.getId(), monitoring.toPriceCriteria());
	}

	@Transactional
	public PriceObservationResponse saveObservation(
			long ownerId, long monitoringId, Instant observedAt, PriceCalculationResult result) {
		return saveObservation(requireOwnedMonitoring(ownerId, monitoringId), observedAt, result);
	}

	@Transactional
	public PriceObservationResponse saveScheduledObservation(
			long monitoringId, Instant observedAt, PriceCalculationResult result) {
		return saveObservation(requireMonitoring(monitoringId), observedAt, result);
	}

	private PriceObservationResponse saveObservation(
			Monitoring monitoring, Instant observedAt, PriceCalculationResult result) {
		if (!monitoring.isActive()) {
			throw new MonitoringInactiveException(monitoring.getId());
		}
		PriceObservation observation = new PriceObservation(monitoring, observedAt, result);
		observationRepository.save(observation);
		monitoring.recordSuccessfulCheck(observedAt);
		return PriceObservationResponse.from(observation);
	}

	@Transactional
	public void recordFailure(long monitoringId, Instant checkedAt, String error) {
		Monitoring monitoring = requireMonitoring(monitoringId);
		monitoring.recordFailedCheck(checkedAt, error);
	}

	@Transactional
	public void deactivate(long ownerId, long monitoringId) {
		requireOwnedMonitoring(ownerId, monitoringId).deactivate();
	}

	@Transactional(readOnly = true)
	public List<PriceObservationResponse> findObservations(long ownerId, long monitoringId) {
		requireOwnedMonitoring(ownerId, monitoringId);
		return observationRepository.findByMonitoringIdOrderByObservedAtAsc(monitoringId).stream()
				.map(PriceObservationResponse::from)
				.toList();
	}

	private Monitoring requireMonitoring(long monitoringId) {
		return monitoringRepository.findById(monitoringId)
				.orElseThrow(() -> new MonitoringNotFoundException(monitoringId));
	}

	private Monitoring requireOwnedMonitoring(long ownerId, long monitoringId) {
		return monitoringRepository.findByIdAndOwnerId(monitoringId, ownerId)
				.orElseThrow(() -> new MonitoringNotFoundException(monitoringId));
	}

	public record CardIdentity(
			String cardName,
			String cardVersion,
			String expansionName,
			String expansionCode) {
	}

	public record RefreshTarget(long monitoringId, PriceCriteria criteria) {
	}
}
