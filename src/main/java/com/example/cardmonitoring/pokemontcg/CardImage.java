package com.example.cardmonitoring.pokemontcg;

public record CardImage(String smallUrl, String largeUrl, String source, String externalCardId) {

	public CardImage(String smallUrl, String largeUrl, String source) {
		this(smallUrl, largeUrl, source, null);
	}

	public boolean hasImage() {
		return smallUrl != null || largeUrl != null;
	}
}
