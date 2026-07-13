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
@Table(name = "user_collection_card")
public class UserCollectionCard {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_collection_id", nullable = false)
	private UserCollection userCollection;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "collection_card_id", nullable = false)
	private CollectionCard collectionCard;

	@Column(nullable = false)
	private boolean owned;

	@Column(name = "owned_at")
	private Instant ownedAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected UserCollectionCard() {
	}

	public UserCollectionCard(UserCollection userCollection, CollectionCard collectionCard, Instant now) {
		this.userCollection = Objects.requireNonNull(userCollection, "userCollection is required");
		this.collectionCard = Objects.requireNonNull(collectionCard, "collectionCard is required");
		this.owned = false;
		this.updatedAt = Objects.requireNonNull(now, "now is required");
	}

	public void setOwned(boolean owned, Instant now) {
		this.owned = owned;
		this.ownedAt = owned ? Objects.requireNonNull(now, "now is required") : null;
		this.updatedAt = Objects.requireNonNull(now, "now is required");
	}

	public Long getId() {
		return id;
	}

	public UserCollection getUserCollection() {
		return userCollection;
	}

	public CollectionCard getCollectionCard() {
		return collectionCard;
	}

	public boolean isOwned() {
		return owned;
	}

	public Instant getOwnedAt() {
		return ownedAt;
	}
}
