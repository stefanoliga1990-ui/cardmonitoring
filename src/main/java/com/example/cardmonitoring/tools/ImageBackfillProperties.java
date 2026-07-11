package com.example.cardmonitoring.tools;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cardmonitoring.tools.image-backfill")
public class ImageBackfillProperties {

	private boolean enabled;
	private Duration delay = Duration.ofMillis(300);
	private int pokemonPageSize = 250;

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

	public int getPokemonPageSize() {
		return pokemonPageSize;
	}

	public void setPokemonPageSize(int pokemonPageSize) {
		this.pokemonPageSize = Math.max(1, Math.min(pokemonPageSize, 250));
	}
}
