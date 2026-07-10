package com.example.cardmonitoring.telegram;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class TelegramClient {

	private static final Logger LOGGER = LoggerFactory.getLogger(TelegramClient.class);

	private final RestClient restClient;
	private final TelegramProperties properties;

	public TelegramClient(
			@Qualifier("telegramRestClient") RestClient restClient,
			TelegramProperties properties) {
		this.restClient = restClient;
		this.properties = properties;
	}

	public void registerWebhook() {
		if (!properties.isEnabled()) {
			LOGGER.info("Telegram webhook registration skipped because Telegram integration is disabled");
			return;
		}

		String webhookUrl = properties.webhookUrl();
		LOGGER.info("Registering Telegram webhook: url={}", webhookUrl);
		callTelegram("setWebhook", Map.of(
				"url", webhookUrl,
				"secret_token", properties.getWebhookSecret(),
				"allowed_updates", List.of("message")));
		LOGGER.info("Telegram webhook registration completed: url={}", webhookUrl);
	}

	public void sendMessage(String chatId, String text) {
		if (!properties.isEnabled()) {
			throw new TelegramException("Telegram integration is disabled");
		}
		if (chatId == null || chatId.isBlank()) {
			throw new IllegalArgumentException("chatId is required");
		}
		if (text == null || text.isBlank()) {
			throw new IllegalArgumentException("text is required");
		}

		callTelegram("sendMessage", Map.of(
				"chat_id", chatId,
				"text", truncateTelegramMessage(text),
				"disable_web_page_preview", true));
	}

	private void callTelegram(String method, Map<String, Object> body) {
		try {
			restClient.post()
					.uri("/bot{token}/{method}", properties.getBotToken(), method)
					.body(body)
					.retrieve()
					.onStatus(HttpStatusCode::isError, (request, response) -> {
						throw new TelegramException("Telegram API returned HTTP " + response.getStatusCode().value());
					})
					.toBodilessEntity();
		}
		catch (ResourceAccessException exception) {
			throw new TelegramException("Telegram API is not reachable", exception);
		}
		catch (RestClientResponseException exception) {
			throw new TelegramException("Telegram API returned HTTP " + exception.getStatusCode().value(), exception);
		}
	}

	private static String truncateTelegramMessage(String text) {
		return text.length() <= 4096 ? text : text.substring(0, 4090) + "\n[...]";
	}
}
