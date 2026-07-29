package com.example.cardmonitoring.tools;

import java.time.Instant;
import java.util.List;

public record ImageCoverageAuditStatusResponse(
		boolean running,
		Instant startedAt,
		Instant finishedAt,
		int processedExpansions,
		int totalExpansions,
		String currentExpansion,
		int failedExpansions,
		String lastError,
		List<IncompleteImageExpansionResponse> incompleteExpansions) {
}
