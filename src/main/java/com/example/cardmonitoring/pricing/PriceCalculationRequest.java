package com.example.cardmonitoring.pricing;

public record PriceCalculationRequest(
		long expansionId,
		long blueprintId,
		String language,
		String condition,
		boolean firstEdition,
		boolean reverse,
		boolean graded,
		boolean signed,
		boolean altered) {

	PriceCriteria toPriceCriteria() {
		return new PriceCriteria(
				blueprintId,
				expansionId,
				language,
				condition,
				firstEdition,
				reverse,
				graded,
				signed,
				altered);
	}
}
