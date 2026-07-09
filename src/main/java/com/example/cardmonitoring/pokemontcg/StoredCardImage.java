package com.example.cardmonitoring.pokemontcg;

import java.time.Duration;
import java.time.Instant;

import com.example.cardmonitoring.catalog.CatalogCard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "card_image", uniqueConstraints = @UniqueConstraint(
		name = "uk_card_image_identity",
		columnNames = { "expansion_id", "blueprint_id", "collector_number", "image_source" }))
public class StoredCardImage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "expansion_id", nullable = false)
	private long expansionId;

	@Column(name = "blueprint_id", nullable = false)
	private long blueprintId;

	@Column(name = "card_name", nullable = false)
	private String cardName;

	@Column(name = "card_version")
	private String cardVersion;

	@Column(name = "collector_number", nullable = false, length = 50)
	private String collectorNumber;

	@Column(name = "image_source", nullable = false, length = 50)
	private String imageSource;

	@Column(name = "external_card_id", length = 100)
	private String externalCardId;

	@Column(name = "small_image_url", length = 1000)
	private String smallImageUrl;

	@Column(name = "large_image_url", length = 1000)
	private String largeImageUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private CardImageStatus status;

	@Column(name = "verification_failures", nullable = false)
	private int verificationFailures;

	@Column(name = "last_verified_at")
	private Instant lastVerifiedAt;

	@Column(name = "verification_failed_at")
	private Instant verificationFailedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "last_used_at")
	private Instant lastUsedAt;

	protected StoredCardImage() {
	}

	public StoredCardImage(CatalogCard card, String collectorNumber, CardImage image, Instant now) {
		this.expansionId = card.expansionId();
		this.blueprintId = card.blueprintId();
		this.cardName = requiredText(card.cardName(), "cardName", 255);
		this.cardVersion = optionalText(card.cardVersion(), 255);
		this.collectorNumber = requiredText(collectorNumber, "collectorNumber", 50);
		this.imageSource = requiredText(image.source(), "imageSource", 50);
		this.createdAt = now;
		refresh(card, collectorNumber, image, now);
	}

	public void refresh(CatalogCard card, String collectorNumber, CardImage image, Instant now) {
		this.expansionId = card.expansionId();
		this.blueprintId = card.blueprintId();
		this.cardName = requiredText(card.cardName(), "cardName", 255);
		this.cardVersion = optionalText(card.cardVersion(), 255);
		this.collectorNumber = requiredText(collectorNumber, "collectorNumber", 50);
		this.imageSource = requiredText(image.source(), "imageSource", 50);
		this.externalCardId = optionalText(image.externalCardId(), 100);
		this.smallImageUrl = optionalText(image.smallUrl(), 1000);
		this.largeImageUrl = optionalText(image.largeUrl(), 1000);
		this.status = CardImageStatus.ACTIVE;
		this.verificationFailures = 0;
		this.lastVerifiedAt = now;
		this.verificationFailedAt = null;
		this.updatedAt = now;
		this.lastUsedAt = now;
	}

	public void recordUse(Instant now) {
		this.lastUsedAt = now;
		this.updatedAt = now;
	}

	public void recordVerificationFailure(Instant now) {
		this.status = hasImage() ? CardImageStatus.REFRESH_NEEDED : CardImageStatus.BROKEN;
		this.verificationFailures += 1;
		this.verificationFailedAt = now;
		this.updatedAt = now;
	}

	public boolean isFresh(Instant now, Duration maximumAge) {
		return status == CardImageStatus.ACTIVE
				&& hasImage()
				&& lastVerifiedAt != null
				&& !lastVerifiedAt.isBefore(now.minus(maximumAge));
	}

	public boolean hasImage() {
		return smallImageUrl != null || largeImageUrl != null;
	}

	public CardImage toCardImage() {
		return new CardImage(smallImageUrl, largeImageUrl, imageSource, externalCardId);
	}

	public Long getId() {
		return id;
	}

	public long getExpansionId() {
		return expansionId;
	}

	public long getBlueprintId() {
		return blueprintId;
	}

	public String getCollectorNumber() {
		return collectorNumber;
	}

	public String getImageSource() {
		return imageSource;
	}

	public String getExternalCardId() {
		return externalCardId;
	}

	public CardImageStatus getStatus() {
		return status;
	}

	public int getVerificationFailures() {
		return verificationFailures;
	}

	public Instant getLastVerifiedAt() {
		return lastVerifiedAt;
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

	private static String optionalText(String value, int maximumLength) {
		String normalized = value == null ? "" : value.trim();
		if (normalized.isEmpty()) {
			return null;
		}
		if (normalized.length() > maximumLength) {
			throw new IllegalArgumentException("optional text is too long");
		}
		return normalized;
	}
}
