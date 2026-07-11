package com.example.cardmonitoring.pokemontcg;

import java.util.List;

public record PokemonTcgCardPage(
		List<PokemonTcgCardCandidate> cards,
		int page,
		int pageSize,
		int count,
		int totalCount) {
}
