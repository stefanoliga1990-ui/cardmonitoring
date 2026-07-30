package com.example.cardmonitoring.pokemontcg;

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
@Table(name = "tcg_reference_card")
public class TcgCollectorReferenceCard {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reference_set_id", nullable = false)
	private TcgCollectorReferenceSet referenceSet;

	@Column(name = "source_name", nullable = false, length = 255)
	private String sourceName;

	@Column(name = "normalized_name", nullable = false, length = 255)
	private String normalizedName;

	@Column(name = "source_number", nullable = false, length = 100)
	private String sourceNumber;

	@Column(name = "normalized_collector_number", nullable = false, length = 100)
	private String normalizedCollectorNumber;

	protected TcgCollectorReferenceCard() {
	}

	public TcgCollectorReferenceCard(
			TcgCollectorReferenceSet referenceSet,
			String sourceName,
			String normalizedName,
			String sourceNumber,
			String normalizedCollectorNumber) {
		this.referenceSet = referenceSet;
		this.sourceName = sourceName;
		this.normalizedName = normalizedName;
		this.sourceNumber = sourceNumber;
		this.normalizedCollectorNumber = normalizedCollectorNumber;
	}

	public TcgCollectorReferenceSet getReferenceSet() {
		return referenceSet;
	}

	public String getSourceName() {
		return sourceName;
	}

	public String getNormalizedName() {
		return normalizedName;
	}

	public String getSourceNumber() {
		return sourceNumber;
	}

	public String getNormalizedCollectorNumber() {
		return normalizedCollectorNumber;
	}
}
