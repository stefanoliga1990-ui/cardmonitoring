package com.example.cardmonitoring.telegram;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telegram")
public class TelegramProperties {

	private URI baseUrl = URI.create("https://api.telegram.org");
	private boolean enabled;
	private String botToken = "";
	private String botUsername = "";
	private String webhookSecret = "";
	private String appPublicUrl = "";
	private Duration connectTimeout = Duration.ofSeconds(5);
	private Duration readTimeout = Duration.ofSeconds(15);

	public URI getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(URI baseUrl) {
		this.baseUrl = Objects.requireNonNull(baseUrl, "Telegram base URL is required");
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getBotToken() {
		return botToken;
	}

	public void setBotToken(String botToken) {
		this.botToken = normalize(botToken);
	}

	public String getBotUsername() {
		return botUsername;
	}

	public void setBotUsername(String botUsername) {
		String normalized = normalize(botUsername);
		this.botUsername = normalized.startsWith("@") ? normalized.substring(1) : normalized;
	}

	public String getWebhookSecret() {
		return webhookSecret;
	}

	public void setWebhookSecret(String webhookSecret) {
		this.webhookSecret = normalize(webhookSecret);
	}

	public String getAppPublicUrl() {
		return appPublicUrl;
	}

	public void setAppPublicUrl(String appPublicUrl) {
		this.appPublicUrl = normalize(appPublicUrl);
		if (this.appPublicUrl.endsWith("/")) {
			this.appPublicUrl = this.appPublicUrl.substring(0, this.appPublicUrl.length() - 1);
		}
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
			throw new IllegalArgumentException("Telegram base URL must be an absolute HTTP URL");
		}
		requirePositive(connectTimeout, "connect timeout");
		requirePositive(readTimeout, "read timeout");
		if (enabled) {
			requireConfigured(botToken, "TELEGRAM_BOT_TOKEN");
			requireConfigured(botUsername, "TELEGRAM_BOT_USERNAME");
			requireConfigured(webhookSecret, "TELEGRAM_WEBHOOK_SECRET");
			requireConfigured(appPublicUrl, "APP_PUBLIC_URL");
			if (!appPublicUrl.startsWith("https://")) {
				throw new IllegalArgumentException("APP_PUBLIC_URL must be an HTTPS URL when Telegram is enabled");
			}
		}
	}

	public String webhookUrl() {
		return appPublicUrl + "/api/telegram/webhook";
	}

	public String botDeepLink(String token) {
		return "https://t.me/" + botUsername + "?start=" + token;
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim();
	}

	private static void requireConfigured(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " is required when Telegram is enabled");
		}
	}

	private static Duration requirePositive(Duration value, String propertyName) {
		if (value == null || value.isZero() || value.isNegative()) {
			throw new IllegalArgumentException("Telegram " + propertyName + " must be positive");
		}
		return value;
	}
}
