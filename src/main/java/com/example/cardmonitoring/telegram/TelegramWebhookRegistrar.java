package com.example.cardmonitoring.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class TelegramWebhookRegistrar {

	private static final Logger LOGGER = LoggerFactory.getLogger(TelegramWebhookRegistrar.class);

	private final TelegramClient telegramClient;

	TelegramWebhookRegistrar(TelegramClient telegramClient) {
		this.telegramClient = telegramClient;
	}

	@EventListener(ApplicationReadyEvent.class)
	void registerWebhook() {
		try {
			telegramClient.registerWebhook();
		}
		catch (RuntimeException exception) {
			LOGGER.warn("Telegram webhook registration failed: {}", exception.getMessage());
		}
	}
}
