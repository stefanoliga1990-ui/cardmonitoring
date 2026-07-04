package com.example.cardmonitoring.catalog;

public record CatalogCard(
		long blueprintId,
		String cardName,
		String cardVersion,
		long expansionId,
		String expansionName,
		String expansionCode) {
}
