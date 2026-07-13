package com.example.cardmonitoring.user;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
public class AppUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 50)
	private String username;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(nullable = false)
	private boolean enabled;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "telegram_chat_id", length = 50)
	private String telegramChatId;

	@Column(name = "telegram_username")
	private String telegramUsername;

	@Column(name = "telegram_enabled", nullable = false)
	private boolean telegramEnabled;

	@Column(name = "telegram_linked_at")
	private Instant telegramLinkedAt;

	@Column(name = "telegram_last_error", length = 1000)
	private String telegramLastError;

	protected AppUser() {
	}

	public AppUser(String username, String passwordHash) {
		this.username = Objects.requireNonNull(username, "username is required");
		this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash is required");
		this.enabled = true;
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getUsername() {
		return username;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public String getTelegramChatId() {
		return telegramChatId;
	}

	public String getTelegramUsername() {
		return telegramUsername;
	}

	public boolean isTelegramEnabled() {
		return telegramEnabled;
	}

	public Instant getTelegramLinkedAt() {
		return telegramLinkedAt;
	}

	public String getTelegramLastError() {
		return telegramLastError;
	}

	public void linkTelegram(String chatId, String username, Instant linkedAt) {
		String normalizedChatId = normalizeRequired(chatId, "chatId", 50);
		this.telegramChatId = normalizedChatId;
		this.telegramUsername = normalizeOptional(username, 255);
		this.telegramEnabled = true;
		this.telegramLinkedAt = Objects.requireNonNull(linkedAt, "linkedAt is required");
		this.telegramLastError = null;
	}

	public void unlinkTelegram() {
		this.telegramChatId = null;
		this.telegramUsername = null;
		this.telegramEnabled = false;
		this.telegramLinkedAt = null;
		this.telegramLastError = null;
	}

	public void recordTelegramSuccess() {
		this.telegramLastError = null;
	}

	public void recordTelegramError(String error) {
		this.telegramLastError = normalizeOptional(error, 1000);
	}

	public void changePasswordHash(String passwordHash) {
		this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash is required");
	}

	private static String normalizeRequired(String value, String fieldName, int maximumLength) {
		String normalized = normalizeOptional(value, maximumLength);
		if (normalized == null) {
			throw new IllegalArgumentException(fieldName + " is required");
		}
		return normalized;
	}

	private static String normalizeOptional(String value, int maximumLength) {
		String normalized = value == null ? "" : value.trim();
		if (normalized.isEmpty()) {
			return null;
		}
		return normalized.length() <= maximumLength ? normalized : normalized.substring(0, maximumLength);
	}
}
