package com.example.cardmonitoring.tools;

public record ImageBackfillExpansionResultResponse(
		long expansionId,
		String expansionName,
		String expansionCode,
		int totalBlueprints,
		int imagesAlreadyPresent,
		int imagesSaved,
		int skippedWithoutCollectorNumber,
		int notFound,
		int errors,
		int imagesInDbAfter) {
}
