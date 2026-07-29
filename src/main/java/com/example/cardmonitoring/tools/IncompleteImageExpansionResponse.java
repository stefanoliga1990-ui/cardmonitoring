package com.example.cardmonitoring.tools;

public record IncompleteImageExpansionResponse(
		long expansionId,
		String expansionName,
		String expansionCode,
		int totalCards,
		int imagesAvailable,
		int missingImages) {
}
