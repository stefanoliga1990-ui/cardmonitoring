package com.example.cardmonitoring.telegram;

import java.time.Instant;

public record TelegramLinkResponse(
		String linkUrl,
		Instant expiresAt,
		String botUsername) {
}
