package com.example.cardmonitoring.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.cardmonitoring.cardtrader.CardTraderException;

@ExtendWith(MockitoExtension.class)
class MonitoringSchedulerTest {

	@Mock
	private MonitoringService monitoringService;

	@Test
	void refreshesActiveMonitoringsSequentiallyAndIsolatesFailures() throws Exception {
		when(monitoringService.findActiveIdsForScheduler()).thenReturn(List.of(1L, 2L, 3L));
		when(monitoringService.refreshScheduled(1L)).thenThrow(CardTraderException.invalidResponse(null));
		when(monitoringService.refreshScheduled(2L)).thenThrow(new MonitoringRefreshInProgressException(2L));
		List<Long> pauses = new ArrayList<>();
		MonitoringScheduler scheduler = new MonitoringScheduler(monitoringService, pauses::add);

		scheduler.refreshActiveMonitorings();

		InOrder order = inOrder(monitoringService);
		order.verify(monitoringService).findActiveIdsForScheduler();
		order.verify(monitoringService).refreshScheduled(1L);
		order.verify(monitoringService).refreshScheduled(2L);
		order.verify(monitoringService).refreshScheduled(3L);
		assertThat(pauses).containsExactly(1_000L, 1_000L);
	}

	@Test
	void stopsCleanlyWhenRateLimitWaitIsInterrupted() throws Exception {
		when(monitoringService.findActiveIdsForScheduler()).thenReturn(List.of(1L, 2L));
		MonitoringScheduler scheduler = new MonitoringScheduler(monitoringService, ignored -> {
			throw new InterruptedException("test interruption");
		});

		scheduler.refreshActiveMonitorings();

		verify(monitoringService).refreshScheduled(1L);
		verify(monitoringService, org.mockito.Mockito.never()).refreshScheduled(2L);
		assertThat(Thread.interrupted()).isTrue();
	}

}
