package com.example.cardmonitoring.collection;

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
@Table(name = "user_collection")
public class UserCollection {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private AppUser owner;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "collection_set_id", nullable = false)
	private CollectionSet collectionSet;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected UserCollection() {
	}

	public UserCollection(AppUser owner, CollectionSet collectionSet, Instant now) {
		this.owner = Objects.requireNonNull(owner, "owner is required");
		this.collectionSet = Objects.requireNonNull(collectionSet, "collectionSet is required");
		this.active = true;
		this.createdAt = Objects.requireNonNull(now, "now is required");
	}

	public void reactivate() {
		this.active = true;
	}

	public Long getId() {
		return id;
	}

	public AppUser getOwner() {
		return owner;
	}

	public CollectionSet getCollectionSet() {
		return collectionSet;
	}

	public boolean isActive() {
		return active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
