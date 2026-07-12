package com.example.cardmonitoring.cardtrader;

public record CardTraderMarketplaceOffer(
		long id,
		long blueprintId,
		String name,
		CardTraderExpansionSummary expansion,
		CardTraderMoney price,
		int quantity,
		String description,
		PokemonCardProperties properties,
		boolean graded,
		boolean onVacation) {
}
