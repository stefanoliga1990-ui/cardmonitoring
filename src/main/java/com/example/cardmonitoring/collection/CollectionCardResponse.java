package com.example.cardmonitoring.collection;

public record CollectionCardResponse(
		long id,
		long expansionId,
		long blueprintId,
		String cardName,
		String cardVersion,
		String collectorNumber,
		String imageUrlSmall,
		String imageUrlLarge,
		boolean owned,
		boolean activeMonitoring) {
}
