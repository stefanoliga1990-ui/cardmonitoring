package com.example.cardmonitoring.cardtrader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class CardTraderResponseParserTest {

	private CardTraderResponseParser parser;

	@BeforeEach
	void setUp() {
		parser = new CardTraderResponseParser(new ObjectMapper());
	}

	@Test
	void parsesDirectArray() {
		assertThat(parser.parseGames("""
				[{"id":5,"name":"Pokémon","display_name":"Pokémon"}]
				"""))
				.containsExactly(new CardTraderGame(5, "Pokémon", "Pokémon"));
	}

	@Test
	void parsesArrayWrapper() {
		assertThat(parser.parseCategories("""
				{"array":[{"id":73,"name":"Pokémon Singles","game_id":5}]}
				"""))
				.containsExactly(new CardTraderCategory(73, "Pokémon Singles", 5));
	}

	@Test
	void parsesDataWrapper() {
		assertThat(parser.parseExpansions("""
				{"data":[{"id":1472,"name":"Base Set","code":"bs","game_id":5}]}
				"""))
				.containsExactly(new CardTraderExpansion(1472, "Base Set", "bs", 5));
	}

	@Test
	void parsesNullableBlueprintVersion() {
		assertThat(parser.parseBlueprints("""
				[{"id":111151,"name":"Charizard","version":null,"category_id":73,"expansion_id":1472}]
				"""))
				.containsExactly(new CardTraderBlueprint(111151, "Charizard", null, 73, 1472));
	}

	@Test
	void rejectsUnknownWrapper() {
		assertInvalidResponse("{" + "\"items\":[]}" + "");
	}

	@Test
	void rejectsAmbiguousWrapper() {
		assertInvalidResponse("{" + "\"array\":[],\"data\":[]}" + "");
	}

	@Test
	void rejectsUnexpectedFieldType() {
		assertThatThrownBy(() -> parser.parseExpansions("""
				[{"id":1472,"name":"Base Set","code":"bs","game_id":"5"}]
				"""))
				.isInstanceOfSatisfying(CardTraderException.class, exception ->
						assertThat(exception.getReason()).isEqualTo(CardTraderException.Reason.INVALID_RESPONSE));
	}

	@Test
	void parsesSanitizedRealMarketplaceFixture() throws IOException {
		String responseBody = readFixture("/fixtures/cardtrader/marketplace-products-111151-it.json");

		assertThat(parser.parseMarketplaceProducts(responseBody, 111151))
				.hasSize(3)
				.first()
				.satisfies(offer -> {
					assertThat(offer.id()).isEqualTo(418408517);
					assertThat(offer.blueprintId()).isEqualTo(111151);
					assertThat(offer.name()).isEqualTo("Charizard");
					assertThat(offer.expansion())
							.isEqualTo(new CardTraderExpansionSummary(1472, "bs", "Base Set"));
					assertThat(offer.price()).isEqualTo(new CardTraderMoney(8064, "EUR"));
					assertThat(offer.quantity()).isEqualTo(1);
					assertThat(offer.graded()).isFalse();
					assertThat(offer.onVacation()).isFalse();
					assertThat(offer.properties()).isEqualTo(new PokemonCardProperties(
							"Poor", false, false, "004", false, "Holo Rare", "it",
							"Energy Burn | Fire Spin", true, false));
				});
	}

	@Test
	void marketplaceKeepsMissingPokemonPropertiesAsUnknown() {
		CardTraderMarketplaceOffer offer = parser.parseMarketplaceProducts(marketplaceResponse("{}"), 111151).get(0);

		assertThat(offer.properties()).isEqualTo(
				new PokemonCardProperties(null, null, null, null, null, null, null, null, null, null));
	}

	@Test
	void marketplaceRejectsPokemonPropertyWithUnexpectedType() {
		assertThatThrownBy(() -> parser.parseMarketplaceProducts(
				marketplaceResponse("{\"signed\":\"false\"}"), 111151))
				.isInstanceOfSatisfying(CardTraderException.class, exception ->
						assertThat(exception.getReason()).isEqualTo(CardTraderException.Reason.INVALID_RESPONSE));
	}

	@Test
	void marketplaceReturnsEmptyListWhenRequestedBlueprintKeyIsMissing() {
		assertThat(parser.parseMarketplaceProducts("{\"999\":[]}", 111151)).isEmpty();
	}

	@Test
	void marketplaceRejectsOfferForDifferentBlueprint() {
		String response = marketplaceResponse("{}").replace("\"blueprint_id\":111151", "\"blueprint_id\":999");

		assertThatThrownBy(() -> parser.parseMarketplaceProducts(response, 111151))
				.isInstanceOfSatisfying(CardTraderException.class, exception ->
						assertThat(exception.getReason()).isEqualTo(CardTraderException.Reason.INVALID_RESPONSE));
	}

	@Test
	void marketplaceRejectsInconsistentFlatAndNestedPrices() {
		String response = marketplaceResponse("{}").replace(
				"\"price\":{\"cents\":8064,\"currency\":\"EUR\"}",
				"\"price_cents\":9999,\"price\":{\"cents\":8064,\"currency\":\"EUR\"}");

		assertThatThrownBy(() -> parser.parseMarketplaceProducts(response, 111151))
				.isInstanceOfSatisfying(CardTraderException.class, exception ->
						assertThat(exception.getReason()).isEqualTo(CardTraderException.Reason.INVALID_RESPONSE));
	}

	private void assertInvalidResponse(String responseBody) {
		assertThatThrownBy(() -> parser.parseGames(responseBody))
				.isInstanceOfSatisfying(CardTraderException.class, exception ->
						assertThat(exception.getReason()).isEqualTo(CardTraderException.Reason.INVALID_RESPONSE));
	}

	private String readFixture(String path) throws IOException {
		try (InputStream input = getClass().getResourceAsStream(path)) {
			if (input == null) {
				throw new IOException("Fixture not found: " + path);
			}
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private String marketplaceResponse(String propertiesHash) {
		return """
				{"111151":[{
				  "id":418408517,
				  "blueprint_id":111151,
				  "name_en":"Charizard",
				  "expansion":{"id":1472,"code":"bs","name_en":"Base Set"},
				  "quantity":1,
				  "properties_hash":%s,
				  "graded":false,
				  "on_vacation":false,
				  "price":{"cents":8064,"currency":"EUR"}
				}]}
				""".formatted(propertiesHash);
	}
}
