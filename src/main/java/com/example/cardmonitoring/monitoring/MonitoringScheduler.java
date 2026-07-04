package com.example.cardmonitoring.monitoring;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MonitoringScheduler {

	private static final Logger LOGGER = LoggerFactory.getLogger(MonitoringScheduler.class);
	private static final long MARKETPLACE_CALL_INTERVAL_MILLIS = 1_000L;

	private final MonitoringService monitoringService;
	private final Sleeper sleeper;
	private final AtomicBoolean running = new AtomicBoolean();

	@Autowired
	public MonitoringScheduler(MonitoringService monitoringService) {
		this(monitoringService, Thread::sleep);
	}

	MonitoringScheduler(MonitoringService monitoringService, Sleeper sleeper) {
		this.monitoringService = monitoringService;
		this.sleeper = sleeper;
	}

	@Scheduled(cron = "${monitoring.scheduler.cron}", zone = "${monitoring.scheduler.time-zone}")
	public void refreshActiveMonitorings() {
		if (!running.compareAndSet(false, true)) {
			LOGGER.info("Scheduled monitoring refresh skipped because the previous run is still active");
			return;
		}

		try {
			List<Long> monitorings = monitoringService.findActiveIdsForScheduler();
			LOGGER.info("Starting scheduled refresh for {} active monitorings", monitorings.size());
			for (int index = 0; index < monitorings.size(); index++) {
				if (index > 0 && !waitForNextMarketplaceCall()) {
					return;
				}
				refreshOne(monitorings.get(index));
			}
			LOGGER.info("Scheduled monitoring refresh completed");
		}
		catch (RuntimeException exception) {
			LOGGER.error("Unable to load active monitorings for scheduled refresh", exception);
		}
		finally {
			running.set(false);
		}
	}

	private void refreshOne(long monitoringId) {
		try {
			monitoringService.refreshScheduled(monitoringId);
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
