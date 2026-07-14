package com.example.cardmonitoring.collection;

import java.util.List;

public record CollectionDetailResponse(
		long id,
		long expansionId,
		String name,
		String code,
		int cardCount,
		CollectionImageSyncStatus imageSyncStatus,
		String lastError,
		boolean alreadyPresent,
		List<CollectionCardResponse> cards) {
}
