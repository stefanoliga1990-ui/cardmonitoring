package com.example.cardmonitoring.pokemontcg;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tcg_reference_catalog_state")
public class TcgCollectorReferenceCatalogState {

	@Id
	@Column(name = "catalog_name", length = 100)
	private String catalogName;

	@Column(name = "source_digest", nullable = false, length = 64)
	private String sourceDigest;

	@Column(name = "imported_at", nullable = false)
	private Instant importedAt;

	protected TcgCollectorReferenceCatalogState() {
	}

	public TcgCollectorReferenceCatalogState(String catalogName, String sourceDigest, Instant importedAt) {
		this.catalogName = catalogName;
		this.sourceDigest = sourceDigest;
		this.importedAt = importedAt;
	}

	public String getSourceDigest() {
		return sourceDigest;
	}
}
