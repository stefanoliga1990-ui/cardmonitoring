package com.example.cardmonitoring.tools;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cardmonitoring.tools.image-backfill")
public class ImageBackfillProperties {

	private boolean enabled;
	private Duration delay = Duration.ofSeconds(5);
	private Duration retryDelay = Duration.ofSeconds(1);
	private int maxAttempts = 3;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Duration getDelay() {
		return delay;
	}

	public void setDelay(Duration delay) {
		if (delay == null || delay.isNegative()) {
			throw new IllegalArgumentException("Image backfill delay must be zero or positive");
		}
		this.delay = delay;
	}

	public Duration getRetryDelay() {
		return retryDelay;
	}

	public void setRetryDelay(Duration retryDelay) {
		if (retryDelay == null || retryDelay.isNegative()) {
			throw new IllegalArgumentException("Image backfill retry delay must be zero or positive");
		}
		this.retryDelay = retryDelay;
	}

	public int getMaxAttempts() {
		return maxAttempts;
	}

	public void setMaxAttempts(int maxAttempts) {
		this.maxAttempts = Math.max(1, Math.min(maxAttempts, 5));
	}
}
