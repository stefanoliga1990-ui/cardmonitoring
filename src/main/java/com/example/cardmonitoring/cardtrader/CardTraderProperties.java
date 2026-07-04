package com.example.cardmonitoring.cardtrader;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "cardtrader")
public class CardTraderProperties {

	private URI baseUrl = URI.create("https://api.cardtrader.com/api/v2");
	private String token = "";
	private Duration connectTimeout = Duration.ofSeconds(5);
	private Duration readTimeout = Duration.ofSeconds(30);
	private String expectedCurrency = "EUR";

	public URI getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(URI baseUrl) {
		this.baseUrl = Objects.requireNonNull(baseUrl, "CardTrader base URL is required");
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token == null ? "" : token.trim();
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

	public String getExpectedCurrency() {
		return expectedCurrency;
	}

	public void setExpectedCurrency(String expectedCurrency) {
		String normalizedCurrency = expectedCurrency == null
				? ""
				: expectedCurrency.trim().toUpperCase(Locale.ROOT);
		if (!normalizedCurrency.matches("[A-Z]{3}")) {
			throw new IllegalArgumentException("CardTrader expected currency must be a three-letter ISO code");
		}
		this.expectedCurrency = normalizedCurrency;
	}

	String requireToken() {
		if (!StringUtils.hasText(token)) {
			throw CardTraderException.configuration("CardTrader token is not configured");
		}
		return token;
	}

	void validate() {
		String scheme = baseUrl.getScheme();
		if (baseUrl.getHost() == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
			throw new IllegalArgumentException("CardTrader base URL must be an absolute HTTP URL");
		}
		requirePositive(connectTimeout, "connect timeout");
		requirePositive(readTimeout, "read timeout");
	}

	private static Duration requirePositive(Duration value, String propertyName) {
		if (value == null || value.isZero() || value.isNegative()) {
			throw new IllegalArgumentException("CardTrader " + propertyName + " must be positive");
		}
		return value;
	}
}
