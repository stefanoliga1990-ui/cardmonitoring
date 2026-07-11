package com.example.cardmonitoring.tools;

import java.time.Instant;

public record ImageBackfillStatusResponse(
		boolean enabled,
		ImageBackfillRunState state,
		Instant startedAt,
		Instant finishedAt,
		int pokemonPagesFetched,
		int totalPokemonCards,
		int totalExpansions,
		int processedExpansions,
		int totalBlueprints,
		int processedBlueprints,
		int savedImages,
		int updatedImages,
		int alreadyPresentImages,
		int skippedWithoutCollectorNumber,
		int skippedWithoutPokemonCandidate,
		int skippedWithoutReliableMatch,
		int errors,
		String currentExpansion,
		String currentCard,
		String lastError) {
}
