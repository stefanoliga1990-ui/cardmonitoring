package com.example.cardmonitoring.monitoring;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.cardmonitoring.cardtrader.CardTraderException;
import com.example.cardmonitoring.cardtrader.CardTraderProperties;
import com.example.cardmonitoring.catalog.CatalogCard;
import com.example.cardmonitoring.catalog.CatalogService;
import com.example.cardmonitoring.monitoring.MonitoringPersistenceService.CardIdentity;
import com.example.cardmonitoring.monitoring.MonitoringPersistenceService.NotificationTarget;
import com.example.cardmonitoring.monitoring.MonitoringPersistenceService.RefreshTarget;
import com.example.cardmonitoring.pokemontcg.CardImage;
import com.example.cardmonitoring.pokemontcg.CardImageService;
import com.example.cardmonitoring.pricing.PriceCalculationResult;
import com.example.cardmonitoring.pricing.PriceCalculationService;
import com.example.cardmonitoring.pricing.PriceCriteria;

@Service
public class MonitoringService {

	private static final Logger LOGGER = LoggerFactory.getLogger(MonitoringService.class);
	private static final String UNEXPECTED_CALCULATION_ERROR = "Unexpected price calculation error";

	private final CatalogService catalogService;
	private final PriceCalculationService priceCalculationService;
	private final MonitoringPersistenceService persistenceService;
	private final CardTraderProperties cardTraderProperties;
	private final CardImageService cardImageService;
	private final ConcurrentMap<Long, ReentrantLock> refreshLocks = new ConcurrentHashMap<>();

	public MonitoringService(
			CatalogService catalogService,
			PriceCalculationService priceCalculationService,
			MonitoringPersistenceService persistenceService,
			CardTraderProperties cardTraderProperties,
			CardImageService cardImageService) {
		this.catalogService = catalogService;
		this.priceCalculationService = priceCalculationService;
		this.persistenceService = persistenceService;
		this.cardTraderProperties = cardTraderProperties;
		this.cardImageService = cardImageService;
	}

	public CreatedMonitoringResponse create(long ownerId, CreateMonitoringRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("request is required");
		}
		PriceCriteria criteria = request.toPriceCriteria();
		LOGGER.info(
				"Starting monitoring activation: ownerId={}, blueprintId={}, expansionId={}, language={}, condition={}, firstEdition={}, reverse={}, graded={}, signed={}, altered={}",
				ownerId, criteria.blueprintId(), criteria.expansionId(), criteria.language(), criteria.condition(),
				criteria.firstEdition(), criteria.reverse(), criteria.graded(), criteria.signed(), criteria.altered());
		CatalogCard resolvedCard = catalogService.resolvePokemonCard(criteria.expansionId(), criteria.blueprintId());
		CardImage image = cardImageService.resolve(resolvedCard).orElse(null);
		LOGGER.info("Monitoring activation image lookup completed: ownerId={}, blueprintId={}, imageFound={}, imageSource={}",
				ownerId, criteria.blueprintId(), image != null, image == null ? null : image.source());
		CardIdentity card = new CardIdentity(
				resolvedCard.cardName(),
				resolvedCard.cardVersion(),
				resolvedCard.expansionName(),
				resolvedCard.expansionCode(),
				image == null ? null : image.smallUrl(),
				image == null ? null : image.largeUrl(),
				image == null ? null : image.source());
		PriceCalculationResult result = priceCalculationService.calculate(criteria);
		LOGGER.info(
				"Monitoring activation price calculation completed: ownerId={}, blueprintId={}, hasPrice={}, compatibleOffers={}, usedOffers={}, confidence={}",
				ownerId, criteria.blueprintId(), result.hasPrice(), result.compatibleOffers(), result.usedOffers(),
				result.confidence());
		Instant observedAt = Instant.now();
		CreatedMonitoringResponse created = persistenceService.createWithInitialObservation(
				ownerId,
				card,
				criteria,
				cardTraderProperties.getExpectedCurrency(),
				observedAt,
				result);
		Long createdMonitoringId = created.monitoring() == null ? null : created.monitoring().id();
		LOGGER.info("Monitoring activation completed: ownerId={}, monitoringId={}, blueprintId={}",
				ownerId, createdMonitoringId, criteria.blueprintId());
		return created;
	}

	public List<MonitoringResponse> findActive(long ownerId) {
		return persistenceService.findActive(ownerId);
	}

	public MonitoringResponse findById(long ownerId, long monitoringId) {
		return persistenceService.findById(ownerId, monitoringId);
	}

	public void deactivate(long ownerId, long monitoringId) {
		persistenceService.deactivate(ownerId, monitoringId);
	}

	public MonitoringResponse updatePurchasePrice(long ownerId, long monitoringId, UpdatePurchasePriceRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("request is required");
		}
		return persistenceService.updatePurchasePrice(ownerId, monitoringId, request.purchasePriceCents());
	}

	public PriceObservationResponse refresh(long ownerId, long monitoringId) {
		return refreshInternal(monitoringId, ownerId);
	}

	public List<Long> findActiveIdsForScheduler() {
		return persistenceService.findActiveIdsForScheduler();
	}

	public PriceObservationResponse refreshScheduled(long monitoringId) {
		return refreshInternal(monitoringId, null);
	}

	public ScheduledRefreshResult notificationResultForScheduledSuccess(
			long monitoringId,
			PriceObservationResponse observation) {
		NotificationTarget target = persistenceService.findNotificationTargetForScheduler(monitoringId);
		return ScheduledRefreshResult.success(target, observation);
	}

	public ScheduledRefreshResult refreshScheduledWithNotificationData(long monitoringId) {
		try {
			PriceObservationResponse observation = refreshScheduled(monitoringId);
			NotificationTarget target = persistenceService.findNotificationTargetForScheduler(monitoringId);
			return ScheduledRefreshResult.success(target, observation);
		}
		catch (MonitoringRefreshInProgressException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			return persistenceService.findNotificationTargetOptionalForScheduler(monitoringId)
					.map(target -> ScheduledRefreshResult.failure(target, safeFailureMessage(exception)))
					.orElseThrow(() -> exception);
		}
	}

	private PriceObservationResponse refreshInternal(long monitoringId, Long ownerId) {
		ReentrantLock refreshLock = refreshLocks.computeIfAbsent(monitoringId, ignored -> new ReentrantLock());
		if (!refreshLock.tryLock()) {
			throw new MonitoringRefreshInProgressException(monitoringId);
		}
		try {
			return executeRefresh(monitoringId, ownerId);
		}
		finally {
			refreshLock.unlock();
		}
	}

	private PriceObservationResponse executeRefresh(long monitoringId, Long ownerId) {
		RefreshTarget target = ownerId == null
				? persistenceService.findRefreshTargetForScheduler(monitoringId)
				: persistenceService.findRefreshTarget(ownerId, monitoringId);
		PriceCalculationResult result;
		try {
			result = priceCalculationService.calculate(target.criteria());
		}
		catch (CardTraderException exception) {
			persistenceService.recordFailure(target.monitoringId(), Instant.now(), exception.getMessage());
			throw exception;
		}
		catch (RuntimeException exception) {
			persistenceService.recordFailure(target.monitoringId(), Instant.now(), UNEXPECTED_CALCULATION_ERROR);
			throw exception;
		}
		return ownerId == null
				? persistenceService.saveScheduledObservation(target.monitoringId(), Instant.now(), result)
				: persistenceService.saveObservation(ownerId, target.monitoringId(), Instant.now(), result);
	}

	public List<PriceObservationResponse> findObservations(long ownerId, long monitoringId) {
		return persistenceService.findObservations(ownerId, monitoringId);
	}

	private static String safeFailureMessage(RuntimeException exception) {
		String message = exception.getMessage();
		return message == null || message.isBlank() ? UNEXPECTED_CALCULATION_ERROR : message;
	}

	public record ScheduledRefreshResult(
			NotificationTarget target,
			PriceObservationResponse observation,
			String error) {

		public static ScheduledRefreshResult success(NotificationTarget target, PriceObservationResponse observation) {
			return new ScheduledRefreshResult(target, observation, null);
		}

		public static ScheduledRefreshResult failure(NotificationTarget target, String error) {
			return new ScheduledRefreshResult(target, null, error);
		}

		public boolean success() {
			return observation != null;
		}
	}

}
