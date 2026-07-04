package com.example.cardmonitoring.cardtrader;

public record CardTraderMarketplaceOffer(
		long id,
		long blueprintId,
		String name,
		CardTraderExpansionSummary expansion,
		CardTraderMoney price,
		int quantity,
		PokemonCardProperties properties,
		boolean graded,
		boolean onVacation) {
}
