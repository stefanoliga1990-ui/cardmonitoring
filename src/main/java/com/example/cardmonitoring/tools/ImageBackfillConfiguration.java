package com.example.cardmonitoring.tools;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ImageBackfillProperties.class)
class ImageBackfillConfiguration {

	@Bean(destroyMethod = "shutdown")
	ExecutorService imageBackfillExecutor() {
		return Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "image-backfill");
			thread.setDaemon(false);
			return thread;
		});
	}
}
