package com.example.cardmonitoring.pokemontcg;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tcg_reference_set")
public class TcgCollectorReferenceSet {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "source_name", nullable = false, length = 255)
	private String sourceName;

	@Column(name = "normalized_name", nullable = false, length = 255, unique = true)
	private String normalizedName;

	protected TcgCollectorReferenceSet() {
	}

	public TcgCollectorReferenceSet(String sourceName, String normalizedName) {
		this.sourceName = sourceName;
		this.normalizedName = normalizedName;
	}

	public Long getId() {
		return id;
	}

	public String getSourceName() {
		return sourceName;
	}

	public String getNormalizedName() {
		return normalizedName;
	}
}
