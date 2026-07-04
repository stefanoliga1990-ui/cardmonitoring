package com.example.cardmonitoring.pricing;

import java.util.regex.Pattern;

public record PriceCriteria(
		long blueprintId,
		long expansionId,
		String language,
		String condition,
		boolean firstEdition,
		boolean reverse,
		boolean graded,
		boolean signed,
		boolean altered) {

	private static final Pattern LANGUAGE_CODE = Pattern.compile("[a-z]{2}(?:-[A-Z]{2})?");

	public PriceCriteria {
		if (blueprintId <= 0) {
			throw new IllegalArgumentException("blueprintId must be positive");
		}
		if (expansionId <= 0) {
			throw new IllegalArgumentException("expansionId must be positive");
		}
		language = language == null ? "" : language.trim();
		if (!LANGUAGE_CODE.matcher(language).matches()) {
			throw new IllegalArgumentException("language must be a CardTrader language code");
		}
		condition = condition == null ? "" : condition.trim();
		if (condition.isEmpty()) {
			throw new IllegalArgumentException("condition is required");
		}
	}
}
