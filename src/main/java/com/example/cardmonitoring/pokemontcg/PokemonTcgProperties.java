package com.example.cardmonitoring.pokemontcg;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pokemontcg")
public class PokemonTcgProperties {

	private URI baseUrl = URI.create("https://api.pokemontcg.io/v2");
	private String apiKey = "";
	private Duration connectTimeout = Duration.ofSeconds(5);
	private Duration readTimeout = Duration.ofSeconds(15);

	public URI getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(URI baseUrl) {
		this.baseUrl = Objects.requireNonNull(baseUrl, "Pokemon TCG base URL is required");
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey == null ? "" : apiKey.trim();
	}

	public Duration getConnectTimeout() {
		return connectTimeout;
	}

	public void setConnectTimeout(Duration connectTimeout) {
		this.connectTimeout = requirePositive(connectTimeout, "connect timeout");
	}

	public Duration getReadTimeout() {
		return readTimeout;
	}

	public void setReadTimeout(Duration readTimeout) {
		this.readTimeout = requirePositive(readTimeout, "read timeout");
	}

	void validate() {
		String scheme = baseUrl.getScheme();
		if (baseUrl.getHost() == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
			throw new IllegalArgumentException("Pokemon TCG base URL must be an absolute HTTP URL");
		}
		requirePositive(connectTimeout, "connect timeout");
		requirePositive(readTimeout, "read timeout");
	}

	private static Duration requirePositive(Duration value, String propertyName) {
		if (value == null || value.isZero() || value.isNegative()) {
			throw new IllegalArgumentException("Pokemon TCG " + propertyName + " must be positive");
		}
		return value;
	}
}
