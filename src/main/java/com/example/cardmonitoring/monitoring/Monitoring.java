package com.example.cardmonitoring.monitoring;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

import com.example.cardmonitoring.pricing.PriceCriteria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.example.cardmonitoring.user.AppUser;

@Entity
@Table(name = "monitoring")
public class Monitoring {

	private static final int MAXIMUM_ERROR_LENGTH = 1000;
	private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Z]{3}");

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private AppUser owner;

	@Column(name = "blueprint_id", nullable = false)
	private long blueprintId;

	@Column(name = "expansion_id", nullable = false)
	private long expansionId;

	@Column(name = "card_name", nullable = false)
	private String cardName;

	@Column(name = "card_version", nullable = false)
	private String cardVersion;

	@Column(name = "expansion_name", nullable = false)
	private String expansionName;

	@Column(name = "expansion_code", nullable = false, length = 50)
	private String expansionCode;

	@Column(name = "image_url_small", length = 500)
	private String imageUrlSmall;

	@Column(name = "image_url_large", length = 500)
	private String imageUrlLarge;

	@Column(name = "image_source", length = 50)
	private String imageSource;

	@Column(nullable = false, length = 10)
	private String language;

	@Column(name = "card_condition", length = 50)
	private String condition;

	@Column(name = "first_edition", nullable = false)
	private boolean firstEdition;

	@Column(name = "is_reverse", nullable = false)
	private boolean reverse;

	@Column(nullable = false)
	private boolean graded;

	@Column(name = "grading_company", length = 50)
	private String gradingCompany;

	@Column(name = "grading_grade", length = 10)
	private String gradingGrade;

	@Column(nullable = false)
	private boolean signed;

	@Column(nullable = false)
	private boolean altered;

	@Column(nullable = false)
	private boolean active;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(name = "purchase_price_cents")
	private Long purchasePriceCents;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "last_checked_at")
	private Instant lastCheckedAt;

	@Column(name = "last_error", length = MAXIMUM_ERROR_LENGTH)
	private String lastError;

	protected Monitoring() {
	}

	public Monitoring(AppUser owner, String cardName, String cardVersion, String expansionName, String expansionCode,
			String imageUrlSmall, String imageUrlLarge, String imageSource,
			PriceCriteria criteria, String currency) {
		Objects.requireNonNull(criteria, "criteria is required");
		this.owner = Objects.requireNonNull(owner, "owner is required");
		this.blueprintId = criteria.blueprintId();
		this.expansionId = criteria.expansionId();
		this.cardName = requiredText(cardName, "cardName", 255);
		this.cardVersion = requiredText(cardVersion, "cardVersion", 255);
		this.expansionName = requiredText(expansionName, "expansionName", 255);
		this.expansionCode = requiredText(expansionCode, "expansionCode", 50);
		this.imageUrlSmall = optionalText(imageUrlSmall, 500);
		this.imageUrlLarge = optionalText(imageUrlLarge, 500);
		this.imageSource = optionalText(imageSource, 50);
		this.language = criteria.language();
		this.condition = optionalText(criteria.condition(), 50);
		this.firstEdition = criteria.firstEdition();
		this.reverse = criteria.reverse();
		this.graded = criteria.graded();
		this.gradingCompany = optionalText(criteria.gradingCompany(), 50);
		this.gradingGrade = optionalText(criteria.gradingGrade(), 10);
		this.signed = criteria.signed();
		this.altered = criteria.altered();
		this.currency = requiredCurrency(currency);
		this.active = true;
		this.createdAt = Instant.now();
	}

	public void recordSuccessfulCheck(Instant checkedAt) {
		this.lastCheckedAt = Objects.requireNonNull(checkedAt, "checkedAt is required");
		this.lastError = null;
	}

	public void recordFailedCheck(Instant checkedAt, String error) {
		this.lastCheckedAt = Objects.requireNonNull(checkedAt, "checkedAt is required");
		String normalizedError = requiredText(error, "error", Integer.MAX_VALUE);
		this.lastError = normalizedError.length() <= MAXIMUM_ERROR_LENGTH
				? normalizedError
				: normalizedError.substring(0, MAXIMUM_ERROR_LENGTH);
	}

	public void deactivate() {
		this.active = false;
	}

	public void updatePurchasePriceCents(Long purchasePriceCents) {
		if (purchasePriceCents != null && purchasePriceCents <= 0) {
			throw new IllegalArgumentException("purchasePriceCents must be positive or null");
		}
		this.purchasePriceCents = purchasePriceCents;
	}

	public PriceCriteria toPriceCriteria() {
		return new PriceCriteria(
				blueprintId,
				expansionId,
				language,
				condition,
				firstEdition,
				reverse,
				graded,
				gradingCompany,
				gradingGrade,
				signed,
				altered);
	}

	public Long getId() {
		return id;
	}

	public AppUser getOwner() {
		return owner;
	}

	public long getBlueprintId() {
		return blueprintId;
	}

	public long getExpansionId() {
		return expansionId;
	}

	public String getCardName() {
		return cardName;
	}

	public String getCardVersion() {
		return cardVersion;
	}

	public String getExpansionName() {
		return expansionName;
	}

	public String getExpansionCode() {
		return expansionCode;
	}

	public String getImageUrlSmall() {
		return imageUrlSmall;
	}

	public String getImageUrlLarge() {
		return imageUrlLarge;
	}

	public String getImageSource() {
		return imageSource;
	}

	public String getLanguage() {
		return language;
	}

	public String getCondition() {
		return condition;
	}

	public boolean isFirstEdition() {
		return firstEdition;
	}

	public boolean isReverse() {
		return reverse;
	}

	public boolean isGraded() {
		return graded;
	}

	public String getGradingCompany() {
		return gradingCompany;
	}

	public String getGradingGrade() {
		return gradingGrade;
	}

	public boolean isSigned() {
		return signed;
	}

	public boolean isAltered() {
		return altered;
	}

	public boolean isActive() {
		return active;
	}

	public String getCurrency() {
		return currency;
	}

	public Long getPurchasePriceCents() {
		return purchasePriceCents;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getLastCheckedAt() {
		return lastCheckedAt;
	}

	public String getLastError() {
		return lastError;
	}

	private static String requiredCurrency(String value) {
		String currency = requiredText(value, "currency", 3);
		if (!CURRENCY_CODE.matcher(currency).matches()) {
			throw new IllegalArgumentException("currency must be a three-letter uppercase code");
		}
		return currency;
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
