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
		String gradingCompany,
		String gradingGrade,
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
		condition = condition == null ? null : condition.trim();
		if (condition != null && condition.isEmpty()) {
			condition = null;
		}
		gradingCompany = GradingDescriptionParser.normalizeCompany(gradingCompany).orElse(null);
		gradingGrade = GradingDescriptionParser.normalizeGrade(gradingGrade).orElse(null);
		if (!graded) {
			if (condition == null) {
				throw new IllegalArgumentException("condition is required");
			}
			gradingCompany = null;
			gradingGrade = null;
		}
		if (graded && (gradingCompany == null ^ gradingGrade == null)) {
			throw new IllegalArgumentException("grading company and grade must be provided together");
		}
		if (graded && condition == null && gradingCompany == null) {
			throw new IllegalArgumentException("graded criteria require grading company and grade when condition is omitted");
		}
	}
}
