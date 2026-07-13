package com.example.cardmonitoring.collection;

import java.time.Instant;
import java.util.Objects;

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
@Table(name = "collection_card")
public class CollectionCard {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "collection_set_id", nullable = false)
	private CollectionSet collectionSet;

	@Column(name = "expansion_id", nullable = false)
	private long expansionId;

	@Column(name = "blueprint_id", nullable = false)
	private long blueprintId;

	@Column(name = "card_name", nullable = false)
	private String cardName;

	@Column(name = "card_version", nullable = false)
	private String cardVersion;

	@Column(name = "collector_number", length = 50)
	private String collectorNumber;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected CollectionCard() {
	}

	public CollectionCard(
			CollectionSet collectionSet,
			long expansionId,
			long blueprintId,
			String cardName,
			String cardVersion,
			String collectorNumber,
			int sortOrder,
			Instant now) {
		this.collectionSet = Objects.requireNonNull(collectionSet, "collectionSet is required");
		this.expansionId = positive(expansionId, "expansionId");
		this.blueprintId = positive(blueprintId, "blueprintId");
		this.cardName = requiredText(cardName, "cardName", 255);
		this.cardVersion = requiredText(cardVersion, "cardVersion", 255);
		this.collectorNumber = optionalText(collectorNumber, 50);
		this.sortOrder = Math.max(0, sortOrder);
		this.createdAt = Objects.requireNonNull(now, "now is required");
		this.updatedAt = now;
	}

	public void refresh(String cardName, String cardVersion, String collectorNumber, int sortOrder, Instant now) {
		this.cardName = requiredText(cardName, "cardName", 255);
		this.cardVersion = requiredText(cardVersion, "cardVersion", 255);
		this.collectorNumber = optionalText(collectorNumber, 50);
		this.sortOrder = Math.max(0, sortOrder);
		this.updatedAt = Objects.requireNonNull(now, "now is required");
	}

	public Long getId() {
		return id;
	}

	public CollectionSet getCollectionSet() {
		return collectionSet;
	}

	public long getExpansionId() {
		return expansionId;
	}

	public long getBlueprintId() {
		return blueprintId;
	}

	public String getCardName() {
		return cardName;
	}

	public String getCardVersion() {
		return cardVersion;
	}

	public String getCollectorNumber() {
		return collectorNumber;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	private static long positive(long value, String fieldName) {
		if (value <= 0) {
			throw new IllegalArgumentException(fieldName + " must be positive");
		}
		return value;
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
