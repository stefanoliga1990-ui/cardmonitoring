package com.example.cardmonitoring.catalog;

import java.util.regex.Pattern;

/**
 * Normalizes the display/search name supplied by CardTrader without changing
 * meaningful parts of a card identity (for example, owner's names).
 */
public final class CardNameNormalizer {

	private static final Pattern TRAILING_NUMERIC_LEVEL = Pattern.compile(
			"(?i)\\s+lv\\.?\\s*\\d+(?:[.,]\\d+)?\\s*$");

	private CardNameNormalizer() {
	}

	public static String withoutTrailingNumericLevel(String cardName) {
		if (cardName == null) {
			return null;
		}
		return TRAILING_NUMERIC_LEVEL.matcher(cardName).replaceFirst("").trim();
	}
}
