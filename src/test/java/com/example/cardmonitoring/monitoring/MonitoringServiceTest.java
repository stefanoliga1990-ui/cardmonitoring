package com.example.cardmonitoring.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.cardmonitoring.cardtrader.CardTraderException;
import com.example.cardmonitoring.cardtrader.CardTraderProperties;
import com.example.cardmonitoring.catalog.CatalogCard;
import com.example.cardmonitoring.catalog.CatalogService;
import com.example.cardmonitoring.monitoring.MonitoringPersistenceService.CardIdentity;
import com.example.cardmonitoring.monitoring.MonitoringPersistenceService.RefreshTarget;
import com.example.cardmonitoring.pokemontcg.CardImage;
import com.example.cardmonitoring.pokemontcg.CardImageService;
import com.example.cardmonitoring.pricing.ConfidenceLevel;
import com.example.cardmonitoring.pricing.PriceCalculationResult;
import com.example.cardmonitoring.pricing.PriceCalculationService;
import com.example.cardmonitoring.pricing.PriceCriteria;

@ExtendWith(MockitoExtension.class)
class MonitoringServiceTest {

	private static final PriceCriteria CRITERIA = new PriceCriteria(
			111151, 1472, "it", "Near Mint", false, false, false, false, false);
	private static final PriceCalculationResult RESULT = new PriceCalculationResult(
			"EUR", new BigDecimal("10100.00"), 10_000L, 10_200L, 3, 3, ConfidenceLevel.MEDIUM);
	private static final CatalogCard CARD = new CatalogCard(
			111151, "Charizard", "Holo Rare | 4/102", 1472, "Base Set", "bs");

	@Mock
	private CatalogService catalogService;

	@Mock
	private PriceCalculationService priceCalculationService;

	@Mock
	private MonitoringPersistenceService persistenceService;

	@Mock
	private CardImageService cardImageService;

	private MonitoringService service;

	@BeforeEach
	void setUp() {
		CardTraderProperties properties = new CardTraderProperties();
		properties.setExpectedCurrency("EUR");
		service = new MonitoringService(
				catalogService, priceCalculationService, persistenceService, properties, cardImageService);
	}

	@Test
	void createsMonitoringOnlyAfterCatalogResolutionAndInitialCalculation() {
		CreateMonitoringRequest request = request();
		when(catalogService.resolvePokemonCard(1472, 111151)).thenReturn(CARD);
		when(cardImageService.resolve(CARD)).thenReturn(java.util.Optional.of(new CardImage(
				"https://images.test/small.png", "https://images.test/large.png", "POKEMON_TCG_API")));
		when(priceCalculationService.calculate(CRITERIA)).thenReturn(RESULT);
		CreatedMonitoringResponse expected = new CreatedMonitoringResponse(null, null);
		when(persistenceService.createWithInitialObservation(
				eq(42L), any(CardIdentity.class), eq(CRITERIA), eq("EUR"), any(Instant.class), eq(RESULT)))
				.thenReturn(expected);

		assertThat(service.create(42L, request)).isSameAs(expected);

		ArgumentCaptor<CardIdentity> identity = ArgumentCaptor.forClass(CardIdentity.class);
		verify(persistenceService).createWithInitialObservation(
				eq(42L), identity.capture(), eq(CRITERIA), eq("EUR"), any(Instant.class), eq(RESULT));
		assertThat(identity.getValue()).isEqualTo(
				new CardIdentity(
						"Charizard",
						"Holo Rare | 4/102",
						"Base Set",
						"bs",
						"https://images.test/small.png",
						"https://images.test/large.png",
						"POKEMON_TCG_API"));
		InOrder order = inOrder(catalogService, cardImageService, priceCalculationService, persistenceService);
		order.verify(catalogService).resolvePokemonCard(1472, 111151);
		order.verify(cardImageService).resolve(CARD);
		order.verify(priceCalculationService).calculate(CRITERIA);
		order.verify(persistenceService).createWithInitialObservation(
				eq(42L), any(CardIdentity.class), eq(CRITERIA), eq("EUR"), any(Instant.class), eq(RESULT));
	}

	@Test
	void doesNotPersistPartialMonitoringWhenInitialCalculationFails() {
		when(catalogService.resolvePokemonCard(1472, 111151)).thenReturn(CARD);
		CardTraderException failure = CardTraderException.invalidResponse(null);
		when(priceCalculationService.calculate(CRITERIA)).thenThrow(failure);

		assertThatThrownBy(() -> service.create(42L, request())).isSameAs(failure);

		verify(persistenceService, never()).createWithInitialObservation(anyLong(), any(), any(), any(), any(), any());
	}

	@Test
	void usesSameRefreshFlowForManualAndFutureAutomaticChecks() {
		RefreshTarget target = new RefreshTarget(9L, CRITERIA);
		PriceObservationResponse expected = observationResponse();
		when(persistenceService.findRefreshTarget(42L, 9L)).thenReturn(target);
		when(priceCalculationService.calculate(CRITERIA)).thenReturn(RESULT);
		when(persistenceService.saveObservation(eq(42L), eq(9L), any(Instant.class), eq(RESULT))).thenReturn(expected);

		assertThat(service.refresh(42L, 9L)).isSameAs(expected);

		InOrder order = inOrder(persistenceService, priceCalculationService);
		order.verify(persistenceService).findRefreshTarget(42L, 9L);
		order.verify(priceCalculationService).calculate(CRITERIA);
		order.verify(persistenceService).saveObservation(eq(42L), eq(9L), any(Instant.class), eq(RESULT));
	}

	@Test
	void recordsCardTraderFailureWithoutCreatingObservation() {
		RefreshTarget target = new RefreshTarget(9L, CRITERIA);
		CardTraderException failure = CardTraderException.invalidResponse(null);
		when(persistenceService.findRefreshTarget(42L, 9L)).thenReturn(target);
		when(priceCalculationService.calculate(CRITERIA)).thenThrow(failure);

		assertThatThrownBy(() -> service.refresh(42L, 9L)).isSameAs(failure);

		verify(persistenceService).recordFailure(eq(9L), any(Instant.class), eq(failure.getMessage()));
		verify(persistenceService, never()).saveObservation(anyLong(), anyLong(), any(), any());
	}

	@Test
	void doesNotCallCardTraderForInactiveMonitoring() {
		when(persistenceService.findRefreshTarget(42L, 9L)).thenThrow(new MonitoringInactiveException(9L));

		assertThatThrownBy(() -> service.refresh(42L, 9L))
				.isInstanceOf(MonitoringInactiveException.class);

		verify(priceCalculationService, never()).calculate(any());
	}

	@Test
	void preventsManualAndAutomaticRefreshFromOverlappingForSameMonitoring() throws Exception {
		RefreshTarget target = new RefreshTarget(9L, CRITERIA);
		PriceObservationResponse expected = observationResponse();
		CountDownLatch calculationStarted = new CountDownLatch(1);
		CountDownLatch releaseCalculation = new CountDownLatch(1);
		when(persistenceService.findRefreshTarget(42L, 9L)).thenReturn(target);
		when(priceCalculationService.calculate(CRITERIA)).thenAnswer(invocation -> {
			calculationStarted.countDown();
			if (!releaseCalculation.await(3, TimeUnit.SECONDS)) {
				throw new IllegalStateException("test calculation was not released");
			}
			return RESULT;
		});
		when(persistenceService.saveObservation(eq(42L), eq(9L), any(Instant.class), eq(RESULT))).thenReturn(expected);

		CompletableFuture<PriceObservationResponse> firstRefresh = CompletableFuture.supplyAsync(
				() -> service.refresh(42L, 9L));
		try {
			assertThat(calculationStarted.await(2, TimeUnit.SECONDS)).isTrue();
			assertThatThrownBy(() -> service.refresh(42L, 9L))
					.isInstanceOf(MonitoringRefreshInProgressException.class);
		}
		finally {
			releaseCalculation.countDown();
		}

		assertThat(firstRefresh.get(2, TimeUnit.SECONDS)).isSameAs(expected);
		verify(priceCalculationService).calculate(CRITERIA);
	}

	private static CreateMonitoringRequest request() {
		return new CreateMonitoringRequest(
				1472, 111151, "it", "Near Mint", false, false, false, false, false);
	}

	private static PriceObservationResponse observationResponse() {
		return new PriceObservationResponse(
				1L,
				Instant.parse("2026-07-03T10:00:00Z"),
				"EUR",
				new BigDecimal("10100.00"),
				10_000L,
				10_200L,
				3,
				3,
				ConfidenceLevel.MEDIUM);
	}
}
