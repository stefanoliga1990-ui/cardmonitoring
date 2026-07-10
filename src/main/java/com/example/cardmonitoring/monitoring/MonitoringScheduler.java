package com.example.cardmonitoring.monitoring;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.cardmonitoring.monitoring.MonitoringService.ScheduledRefreshResult;
import com.example.cardmonitoring.telegram.TelegramScheduledNotificationService;

@Component
public class MonitoringScheduler {

	private static final Logger LOGGER = LoggerFactory.getLogger(MonitoringScheduler.class);
	private static final long MARKETPLACE_CALL_INTERVAL_MILLIS = 1_000L;

	private final MonitoringService monitoringService;
	private final TelegramScheduledNotificationService telegramNotificationService;
	private final Sleeper sleeper;
	private final AtomicBoolean running = new AtomicBoolean();

	@Autowired
	public MonitoringScheduler(
			MonitoringService monitoringService,
			TelegramScheduledNotificationService telegramNotificationService) {
		this(monitoringService, Thread::sleep, telegramNotificationService);
	}

	MonitoringScheduler(MonitoringService monitoringService, Sleeper sleeper) {
		this(monitoringService, sleeper, null);
	}

	MonitoringScheduler(
			MonitoringService monitoringService,
			Sleeper sleeper,
			TelegramScheduledNotificationService telegramNotificationService) {
		this.monitoringService = monitoringService;
		this.sleeper = sleeper;
		this.telegramNotificationService = telegramNotificationService;
	}

	@Scheduled(cron = "${monitoring.scheduler.cron}", zone = "${monitoring.scheduler.time-zone}")
	public void refreshActiveMonitorings() {
		if (!running.compareAndSet(false, true)) {
			LOGGER.info("Scheduled monitoring refresh skipped because the previous run is still active");
			return;
		}

		try {
			List<Long> monitorings = monitoringService.findActiveIdsForScheduler();
			List<ScheduledRefreshResult> notificationResults = new ArrayList<>();
			LOGGER.info("Starting scheduled refresh for {} active monitorings", monitorings.size());
			for (int index = 0; index < monitorings.size(); index++) {
				if (index > 0 && !waitForNextMarketplaceCall()) {
					return;
				}
				refreshOne(monitorings.get(index), notificationResults);
			}
			sendTelegramSummary(notificationResults);
			LOGGER.info("Scheduled monitoring refresh completed");
		}
		catch (RuntimeException exception) {
			LOGGER.error("Unable to load active monitorings for scheduled refresh", exception);
		}
		finally {
			running.set(false);
		}
	}

	private void refreshOne(long monitoringId, List<ScheduledRefreshResult> notificationResults) {
		try {
			PriceObservationResponse observation = monitoringService.refreshScheduled(monitoringId);
			ScheduledRefreshResult result = monitoringService.notificationResultForScheduledSuccess(
					monitoringId, observation);
			if (result != null) {
				notificationResults.add(result);
			}
			LOGGER.info("Scheduled refresh completed for monitoring {}", monitoringId);
		}
		catch (MonitoringRefreshInProgressException exception) {
			LOGGER.info("Scheduled refresh skipped for monitoring {} because another refresh is active",
					monitoringId);
		}
		catch (RuntimeException exception) {
			LOGGER.warn("Scheduled refresh failed for monitoring {}: {}",
					monitoringId, exception.getMessage());
		}
	}

	private void sendTelegramSummary(List<ScheduledRefreshResult> notificationResults) {
		if (telegramNotificationService == null || notificationResults.isEmpty()) {
			return;
		}
		try {
			telegramNotificationService.notifyScheduledRun(notificationResults);
		}
		catch (RuntimeException exception) {
			LOGGER.warn("Telegram scheduled summary failed: {}", exception.getMessage());
		}
	}

	private boolean waitForNextMarketplaceCall() {
		try {
			sleeper.sleep(MARKETPLACE_CALL_INTERVAL_MILLIS);
			return true;
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			LOGGER.warn("Scheduled monitoring refresh interrupted");
			return false;
		}
	}

	@FunctionalInterface
	interface Sleeper {
		void sleep(long milliseconds) throws InterruptedException;
	}
}
