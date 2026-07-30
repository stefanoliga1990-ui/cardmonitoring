package com.example.cardmonitoring.catalog;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts a CardTrader collector number without confusing years or stamp labels for card numbers. */
public final class CollectorNumberParser {

	private static final Pattern NUMBER_WITH_TOTAL = Pattern.compile(
			"(?i)(?:^|\\s)(\\d+[a-z]*)\\s*/\\s*(\\d+[a-z]*)(?=\\s|$)");
	private static final Pattern STANDALONE_NUMBER = Pattern.compile("(?i)^\\s*(\\d+[a-z]*)\\s*$");

	private CollectorNumberParser() {
	}

	public static Optional<String> fromVersion(String version) {
		if (version == null || version.isBlank()) {
			return Optional.empty();
		}
		String relevantPart = version.substring(version.lastIndexOf('|') + 1).trim();
		Matcher withTotal = NUMBER_WITH_TOTAL.matcher(relevantPart);
		if (withTotal.find()) {
			return Optional.of(withTotal.group(1).toUpperCase(Locale.ROOT));
		}
		Matcher standalone = STANDALONE_NUMBER.matcher(relevantPart);
		return standalone.matches()
				? Optional.of(standalone.group(1).toUpperCase(Locale.ROOT))
				: Optional.empty();
	}
}
