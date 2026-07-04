package com.example.cardmonitoring.monitoring;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(MonitoringSchedulerFrequencyTest.TemporarySchedulerConfiguration.class)
@TestPropertySource(properties = {
		"monitoring.scheduler.cron=*/1 * * * * *",
		"monitoring.scheduler.time-zone=UTC"
})
@DirtiesContext
class MonitoringSchedulerFrequencyTest {

	@Autowired
	private MonitoringService monitoringService;

	@Test
	void triggersWithTemporaryOneSecondFrequency() {
		verify(monitoringService, timeout(3_500).atLeastOnce()).findActiveIdsForScheduler();
	}

	@Configuration
	@EnableScheduling
	static class TemporarySchedulerConfiguration {

		@Bean
		MonitoringService monitoringService() {
			return mock(MonitoringService.class);
		}

		@Bean
		MonitoringScheduler monitoringScheduler(MonitoringService monitoringService) {
			return new MonitoringScheduler(monitoringService, ignored -> { });
		}
	}
}
