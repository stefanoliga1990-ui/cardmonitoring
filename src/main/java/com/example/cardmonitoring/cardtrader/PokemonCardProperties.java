package com.example.cardmonitoring.cardtrader;

public record PokemonCardProperties(
		String condition,
		Boolean signed,
		Boolean altered,
		String collectorNumber,
		Boolean firstEdition,
		String rarity,
		String language,
		String attack,
		Boolean tournamentLegal,
		Boolean reverse) {
}
