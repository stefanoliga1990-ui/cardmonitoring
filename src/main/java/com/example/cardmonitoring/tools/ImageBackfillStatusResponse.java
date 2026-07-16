package com.example.cardmonitoring.tools;

import java.time.Instant;
import java.util.List;

public record ImageBackfillStatusResponse(
		boolean enabled,
		ImageBackfillRunState state,
		Instant startedAt,
		Instant finishedAt,
		int totalExpansions,
		int processedExpansions,
		int totalBlueprints,
		int processedBlueprints,
		int savedImages,
		int alreadyPresentImages,
		int currentExpansionTotalBlueprints,
		int currentExpansionProcessedBlueprints,
		int currentExpansionImagesAlreadyPresent,
		int currentExpansionSavedImages,
		int currentExpansionNotFound,
		int skippedWithoutCollectorNumber,
		int skippedWithoutPokemonCandidate,
		int skippedWithoutReliableMatch,
		int errors,
		String currentExpansion,
		String currentCard,
		String lastError,
		List<ImageBackfillExpansionResultResponse> expansionResults) {
}
