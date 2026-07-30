package com.example.cardmonitoring.pokemontcg;

/** Coarse diagnostic reason recorded by the background cache backfill. */
public enum CardImageMissReason {
	REFERENCE_CARD_NOT_FOUND,
	POKEMON_SET_NOT_MAPPED,
	POKEMON_CARD_NOT_FOUND
}
