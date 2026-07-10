package com.example.cardmonitoring.telegram;

import java.time.Instant;
import java.util.Objects;

import com.example.cardmonitoring.user.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "telegram_link_request")
public class TelegramLinkRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private AppUser user;

	@Column(nullable = false, unique = true, length = 64)
	private String token;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	protected TelegramLinkRequest() {
	}

	public TelegramLinkRequest(AppUser user, String token, Instant createdAt, Instant expiresAt) {
		this.user = Objects.requireNonNull(user, "user is required");
		this.token = requireToken(token);
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
		this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt is required");
		if (!expiresAt.isAfter(createdAt)) {
			throw new IllegalArgumentException("expiresAt must be after createdAt");
		}
	}

	public AppUser getUser() {
		return user;
	}

	public String getToken() {
		return token;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public boolean isUsed() {
		return usedAt != null;
	}

	public boolean isExpired(Instant now) {
		return !expiresAt.isAfter(now);
	}

	public void markUsed(Instant usedAt) {
		this.usedAt = Objects.requireNonNull(usedAt, "usedAt is required");
	}

	private static String requireToken(String value) {
		String normalized = value == null ? "" : value.trim();
		if (normalized.isEmpty() || normalized.length() > 64) {
			throw new IllegalArgumentException("token is invalid");
		}
		return normalized;
	}
}
