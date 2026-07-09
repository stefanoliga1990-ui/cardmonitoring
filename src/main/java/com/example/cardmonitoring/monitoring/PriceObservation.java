package com.example.cardmonitoring.monitoring;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import com.example.cardmonitoring.pricing.ConfidenceLevel;
import com.example.cardmonitoring.pricing.PriceCalculationResult;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "price_observation")
public class PriceObservation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "monitoring_id", nullable = false)
	private Monitoring monitoring;

	@Column(name = "observed_at", nullable = false)
	private Instant observedAt;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(name = "average_price_cents", precision = 19, scale = 2)
	private BigDecimal averagePriceCents;

	@Column(name = "minimum_price_cents")
	private Long minimumPriceCents;

	@Column(name = "maximum_price_cents")
	private Long maximumPriceCents;

	@Column(name = "compatible_offers", nullable = false)
	private int compatibleOffers;

	@Column(name = "used_offers", nullable = false)
	private int usedOffers;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ConfidenceLevel confidence;

	protected PriceObservation() {
	}

	public PriceObservation(Monitoring monitoring, Instant observedAt, PriceCalculationResult result) {
		this.monitoring = Objects.requireNonNull(monitoring, "monitoring is required");
		this.observedAt = Objects.requireNonNull(observedAt, "observedAt is required");
		Objects.requireNonNull(result, "result is required");
		if (!monitoring.getCurrency().equals(result.currency())) {
			throw new IllegalArgumentException("result currency does not match monitoring currency");
		}
		validateResult(result);
		this.currency = result.currency();
		this.averagePriceCents = result.averagePriceCents();
		this.minimumPriceCents = result.minimumPriceCents();
		this.maximumPriceCents = result.maximumPriceCents();
		this.compatibleOffers = result.compatibleOffers();
		this.usedOffers = result.usedOffers();
		this.confidence = result.confidence();
	}

	public Long getId() {
		return id;
	}

	public Monitoring getMonitoring() {
		return monitoring;
	}

	public Instant getObservedAt() {
		return observedAt;
	}

	public String getCurrency() {
		return currency;
	}

	public BigDecimal getAveragePriceCents() {
		return averagePriceCents;
	}

	public Long getMinimumPriceCents() {
		return minimumPriceCents;
	}

	public Long getMaximumPriceCents() {
		return maximumPriceCents;
	}

	public int getCompatibleOffers() {
		return compatibleOffers;
	}

	public int getUsedOffers() {
		return usedOffers;
	}

	public ConfidenceLevel getConfidence() {
		return confidence;
	}

	private static void validateResult(PriceCalculationResult result) {
		Objects.requireNonNull(result.confidence(), "result confidence is required");
		if (result.compatibleOffers() < 0
				|| result.usedOffers() < 0
				|| result.usedOffers() > 4
				|| result.usedOffers() > result.compatibleOffers()) {
			throw new IllegalArgumentException("result offer counts are invalid");
		}

		if (result.confidence() == ConfidenceLevel.NO_DATA) {
			if (result.hasPrice() || result.minimumPriceCents() != null || result.maximumPriceCents() != null
					|| result.compatibleOffers() != 0 || result.usedOffers() != 0) {
				throw new IllegalArgumentException("NO_DATA result must not contain price data");
			}
			return;
		}

		if (!result.hasPrice()
				|| result.minimumPriceCents() == null
				|| result.maximumPriceCents() == null
				|| result.averagePriceCents().signum() < 0
				|| result.minimumPriceCents() < 0
				|| result.maximumPriceCents() < result.minimumPriceCents()
				|| result.usedOffers() == 0) {
			throw new IllegalArgumentException("priced result is incomplete or invalid");
		}
	}
}
