package com.example.cardmonitoring.collection;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "collection_set")
public class CollectionSet {

	private static final int MAXIMUM_ERROR_LENGTH = 1000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "expansion_id", nullable = false, unique = true)
	private long expansionId;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, length = 50)
	private String code;

	@Column(name = "card_count", nullable = false)
	private int cardCount;

	@Enumerated(EnumType.STRING)
	@Column(name = "image_sync_status", nullable = false, length = 30)
	private CollectionImageSyncStatus imageSyncStatus;

	@Column(name = "image_sync_started_at")
	private Instant imageSyncStartedAt;

	@Column(name = "image_sync_completed_at")
	private Instant imageSyncCompletedAt;

	@Column(name = "last_error", length = MAXIMUM_ERROR_LENGTH)
	private String lastError;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected CollectionSet() {
	}

	public CollectionSet(long expansionId, String name, String code, int cardCount, Instant now) {
		if (expansionId <= 0) {
			throw new IllegalArgumentException("expansionId must be positive");
		}
		this.expansionId = expansionId;
		this.name = requiredText(name, "name", 255);
		this.code = requiredText(code, "code", 50);
		this.cardCount = Math.max(0, cardCount);
		this.imageSyncStatus = CollectionImageSyncStatus.NOT_STARTED;
		this.createdAt = Objects.requireNonNull(now, "now is required");
		this.updatedAt = now;
	}

	public void updateCatalog(String name, String code, int cardCount, Instant now) {
		this.name = requiredText(name, "name", 255);
		this.code = requiredText(code, "code", 50);
		this.cardCount = Math.max(0, cardCount);
		this.updatedAt = Objects.requireNonNull(now, "now is required");
	}

	public void markSyncStarted(Instant now) {
		this.imageSyncStatus = CollectionImageSyncStatus.RUNNING;
		this.imageSyncStartedAt = Objects.requireNonNull(now, "now is required");
		this.imageSyncCompletedAt = null;
		this.lastError = null;
		this.updatedAt = now;
	}

	public void markSyncCompleted(Instant now) {
		this.imageSyncStatus = CollectionImageSyncStatus.COMPLETED;
		this.imageSyncCompletedAt = Objects.requireNonNull(now, "now is required");
		this.lastError = null;
		this.updatedAt = now;
	}

	public void markSyncFailed(Instant now, String error) {
		this.imageSyncStatus = CollectionImageSyncStatus.PARTIAL_FAILED;
		this.imageSyncCompletedAt = Objects.requireNonNull(now, "now is required");
		this.lastError = truncate(error);
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public long getExpansionId() {
		return expansionId;
	}

	public String getName() {
		return name;
	}

	public String getCode() {
		return code;
	}

	public int getCardCount() {
		return cardCount;
	}

	public CollectionImageSyncStatus getImageSyncStatus() {
		return imageSyncStatus;
	}

	public Instant getImageSyncStartedAt() {
		return imageSyncStartedAt;
	}

	public Instant getImageSyncCompletedAt() {
		return imageSyncCompletedAt;
	}

	public String getLastError() {
		return lastError;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	private static String requiredText(String value, String fieldName, int maximumLength) {
		String normalized = value == null ? "" : value.trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException(fieldName + " is required");
		}
		if (normalized.length() > maximumLength) {
			throw new IllegalArgumentException(fieldName + " is too long");
		}
		return normalized;
	}

	private static String truncate(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.length() <= MAXIMUM_ERROR_LENGTH ? value : value.substring(0, MAXIMUM_ERROR_LENGTH);
	}
}
