package com.example.cardmonitoring.telegram;

import java.time.Instant;

public record TelegramLinkResponse(
		String linkUrl,
		String qrCodeSvg,
		Instant expiresAt,
		String botUsername) {
}
