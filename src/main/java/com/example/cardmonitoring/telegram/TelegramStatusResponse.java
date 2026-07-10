package com.example.cardmonitoring.telegram;

import java.time.Instant;

import com.example.cardmonitoring.user.AppUser;

public record TelegramStatusResponse(
		boolean integrationEnabled,
		boolean linked,
		boolean notificationsEnabled,
		String username,
		Instant linkedAt,
		String lastError) {

	static TelegramStatusResponse disabled() {
		return new TelegramStatusResponse(false, false, false, null, null, null);
	}

	static TelegramStatusResponse from(AppUser user) {
		return new TelegramStatusResponse(
				true,
				user.getTelegramChatId() != null,
				user.isTelegramEnabled(),
				user.getTelegramUsername(),
				user.getTelegramLinkedAt(),
				user.getTelegramLastError());
	}
}
