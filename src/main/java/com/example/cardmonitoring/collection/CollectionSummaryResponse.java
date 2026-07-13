package com.example.cardmonitoring.collection;

import java.time.Instant;

public record CollectionSummaryResponse(
		long id,
		long expansionId,
		String name,
		String code,
		int cardCount,
		CollectionImageSyncStatus imageSyncStatus,
		Instant createdAt) {
}
