package com.example.cardmonitoring.pricing;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

final class GradingDescriptionParser {

	private static final Pattern COMPANY_THEN_GRADE = Pattern.compile(
			"(?i)\\b(PSA|BGS|BECKETT|CGC|GRAAD|SGC|ACE|TAG|ARS|AP(?:\\s+GRADING)?|EUROPEAN\\s+GRADING|EG)\\b"
					+ "[\\s:/#\\-\\[\\]()]*"
					+ "(?:VOTO\\s*)?"
					+ "(10(?:[\\.,]0)?|[1-9](?:[\\.,][05])?)\\b");

	private GradingDescriptionParser() {
	}

	static Optional<GradingDetails> parse(String description) {
		if (!StringUtils.hasText(description)) {
			return Optional.empty();
		}
		Matcher matcher = COMPANY_THEN_GRADE.matcher(description);
		if (!matcher.find()) {
			return Optional.empty();
		}

		Optional<String> company = normalizeCompany(matcher.group(1));
		Optional<String> grade = normalizeGrade(matcher.group(2));
		if (company.isEmpty() || grade.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new GradingDetails(company.get(), grade.get()));
	}

	static Optional<String> normalizeCompany(String value) {
		if (!StringUtils.hasText(value)) {
			return Optional.empty();
		}
		String normalized = value.trim()
				.replaceAll("\\s+", " ")
				.toUpperCase(Locale.ROOT);
		return switch (normalized) {
			case "PSA" -> Optional.of("PSA");
			case "BGS", "BECKETT" -> Optional.of("BGS");
			case "CGC" -> Optional.of("CGC");
			case "GRAAD" -> Optional.of("GRAAD");
			case "SGC" -> Optional.of("SGC");
			case "ACE" -> Optional.of("ACE");
			case "TAG" -> Optional.of("TAG");
			case "ARS" -> Optional.of("ARS");
			case "AP", "AP GRADING" -> Optional.of("AP");
			case "EUROPEAN GRADING", "EG" -> Optional.of("EUROPEAN_GRADING");
			default -> Optional.empty();
		};
	}

	static Optional<String> normalizeGrade(String value) {
		if (!StringUtils.hasText(value)) {
			return Optional.empty();
		}
		String normalized = value.trim().replace(',', '.');
		if (!normalized.matches("10(?:\\.0)?|[1-9](?:\\.[05])?")) {
			return Optional.empty();
		}
		if (normalized.endsWith(".0")) {
			return Optional.of(normalized.substring(0, normalized.length() - 2));
		}
		return Optional.of(normalized);
	}
}
