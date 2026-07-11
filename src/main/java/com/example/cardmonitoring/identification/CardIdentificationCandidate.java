package com.example.cardmonitoring.identification;

public record CardIdentificationCandidate(
		String pokemonTcgCardId,
		String pokemonTcgSetId,
		String cardName,
		String cardNumber,
		String displayNumber,
		String pokemonTcgSetName,
		String pokemonTcgSetSeries,
		Integer pokemonTcgSetPrintedTotal,
		Integer pokemonTcgSetTotal,
		String pokemonTcgSetReleaseDate,
		String imageUrlSmall,
		String imageUrlLarge,
		Long cardTraderExpansionId,
		String cardTraderExpansionName,
		String cardTraderExpansionCode,
		Long cardTraderBlueprintId,
		String cardTraderBlueprintName,
		String cardTraderBlueprintVersion,
		String matchConfidence,
		boolean selectable) {
}
