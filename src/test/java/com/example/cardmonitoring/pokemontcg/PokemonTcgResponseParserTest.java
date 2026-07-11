package com.example.cardmonitoring.pokemontcg;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class PokemonTcgResponseParserTest {

	private PokemonTcgResponseParser parser;

	@BeforeEach
	void setUp() {
		parser = new PokemonTcgResponseParser(new ObjectMapper());
	}

	@Test
	void parsesPagedCardsResponse() {
		PokemonTcgCardPage page = parser.parseCardPage("""
				{
				  "data": [
				    {
				      "id": "base1-4",
				      "name": "Charizard",
				      "number": "4",
				      "set": {
				        "id": "base1",
				        "name": "Base",
				        "series": "Base",
				        "printedTotal": 102,
				        "total": 102,
				        "releaseDate": "1999/01/09"
				      },
				      "images": {
				        "small": "https://images.test/small.png",
				        "large": "https://images.test/large.png"
				      }
				    }
				  ],
				  "page": 2,
				  "pageSize": 250,
				  "count": 1,
				  "totalCount": 321
				}
				""");

		assertThat(page.page()).isEqualTo(2);
		assertThat(page.pageSize()).isEqualTo(250);
		assertThat(page.count()).isEqualTo(1);
		assertThat(page.totalCount()).isEqualTo(321);
		assertThat(page.cards()).containsExactly(new PokemonTcgCardCandidate(
				"base1-4",
				"Charizard",
				"4",
				"base1",
				"Base",
				"Base",
				102,
				102,
				"1999/01/09",
				"https://images.test/small.png",
				"https://images.test/large.png"));
	}
}
