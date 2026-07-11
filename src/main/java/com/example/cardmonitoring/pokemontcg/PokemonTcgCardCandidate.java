package com.example.cardmonitoring.pokemontcg;

public record PokemonTcgCardCandidate(
		String id,
		String name,
		String number,
		String setId,
		String setName,
		String setSeries,
		Integer setPrintedTotal,
		Integer setTotal,
		String setReleaseDate,
		String smallImageUrl,
		String largeImageUrl) {
}
