package com.example.cardmonitoring.cardtrader;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class CardTraderResponseParser {

	private final ObjectMapper objectMapper;

	public CardTraderResponseParser(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	CardTraderInfo parseInfo(String responseBody) {
		JsonNode root = readJson(responseBody);
		if (!root.isObject()) {
			throw CardTraderException.invalidResponse(null);
		}
		return new CardTraderInfo(requiredPositiveLong(root, "id"), requiredText(root, "name"));
	}

	List<CardTraderGame> parseGames(String responseBody) {
		List<CardTraderGame> games = new ArrayList<>();
		for (JsonNode item : extractArray(responseBody)) {
			requireObject(item);
			games.add(new CardTraderGame(
					requiredPositiveLong(item, "id"),
					requiredText(item, "name"),
					requiredText(item, "display_name")));
		}
		return List.copyOf(games);
	}

	List<CardTraderCategory> parseCategories(String responseBody) {
		List<CardTraderCategory> categories = new ArrayList<>();
		for (JsonNode item : extractArray(responseBody)) {
			requireObject(item);
			categories.add(new CardTraderCategory(
					requiredPositiveLong(item, "id"),
					requiredText(item, "name"),
					requiredPositiveLong(item, "game_id")));
		}
		return List.copyOf(categories);
	}

	List<CardTraderExpansion> parseExpansions(String responseBody) {
		List<CardTraderExpansion> expansions = new ArrayList<>();
		for (JsonNode item : extractArray(responseBody)) {
			requireObject(item);
			expansions.add(new CardTraderExpansion(
					requiredPositiveLong(item, "id"),
					requiredText(item, "name"),
					requiredText(item, "code"),
					requiredPositiveLong(item, "game_id")));
		}
		return List.copyOf(expansions);
	}

	List<CardTraderBlueprint> parseBlueprints(String responseBody) {
		List<CardTraderBlueprint> blueprints = new ArrayList<>();
		for (JsonNode item : extractArray(responseBody)) {
			requireObject(item);
			blueprints.add(new CardTraderBlueprint(
					requiredPositiveLong(item, "id"),
					requiredText(item, "name"),
					optionalText(item, "version"),
					requiredPositiveLong(item, "category_id"),
					requiredPositiveLong(item, "expansion_id")));
		}
		return List.copyOf(blueprints);
	}

	List<CardTraderMarketplaceOffer> parseMarketplaceProducts(String responseBody, long blueprintId) {
		JsonNode root = readJson(responseBody);
		if (!root.isObject()) {
			throw CardTraderException.invalidResponse(null);
		}

		JsonNode products = root.get(Long.toString(blueprintId));
		if (products == null) {
			return List.of();
		}
		if (!products.isArray()) {
			throw CardTraderException.invalidResponse(null);
		}

		List<CardTraderMarketplaceOffer> offers = new ArrayList<>();
		for (JsonNode item : products) {
			requireObject(item);
			long productBlueprintId = requiredPositiveLong(item, "blueprint_id");
			if (productBlueprintId != blueprintId) {
				throw CardTraderException.invalidResponse(null);
			}

			offers.add(new CardTraderMarketplaceOffer(
					requiredPositiveLong(item, "id"),
					productBlueprintId,
					requiredText(item, "name_en"),
					parseExpansion(item.get("expansion")),
					parsePrice(item),
					requiredNonNegativeInt(item, "quantity"),
					optionalText(item, "description"),
					parsePokemonProperties(item.get("properties_hash")),
					requiredBoolean(item, "graded"),
					requiredBoolean(item, "on_vacation")));
		}
		return List.copyOf(offers);
	}

	private static CardTraderExpansionSummary parseExpansion(JsonNode expansion) {
		requireObject(expansion);
		return new CardTraderExpansionSummary(
				requiredPositiveLong(expansion, "id"),
				requiredText(expansion, "code"),
				requiredText(expansion, "name_en"));
	}

	private static CardTraderMoney parsePrice(JsonNode product) {
		JsonNode price = product.get("price");
		requireObject(price);
		long cents = requiredNonNegativeLong(price, "cents");
		String currency = requiredCurrency(price, "currency");

		JsonNode flatCents = product.get("price_cents");
		if (flatCents != null && requiredNonNegativeLong(product, "price_cents") != cents) {
			throw CardTraderException.invalidResponse(null);
		}
		JsonNode flatCurrency = product.get("price_currency");
		if (flatCurrency != null && !requiredCurrency(product, "price_currency").equals(currency)) {
			throw CardTraderException.invalidResponse(null);
		}
		return new CardTraderMoney(cents, currency);
	}

	private static PokemonCardProperties parsePokemonProperties(JsonNode properties) {
		if (properties == null || properties.isNull()) {
			return new PokemonCardProperties(null, null, null, null, null, null, null, null, null, null);
		}
		requireObject(properties);
		return new PokemonCardProperties(
				optionalText(properties, "condition"),
				optionalBoolean(properties, "signed"),
				optionalBoolean(properties, "altered"),
				optionalText(properties, "collector_number"),
				optionalBoolean(properties, "first_edition"),
				optionalText(properties, "pokemon_rarity"),
				optionalText(properties, "pokemon_language"),
				optionalText(properties, "pokemon_attack"),
				optionalBoolean(properties, "tournament_legal"),
				optionalBoolean(properties, "pokemon_reverse"));
	}

	private JsonNode extractArray(String responseBody) {
		JsonNode root = readJson(responseBody);
		if (root.isArray()) {
			return root;
		}
		if (!root.isObject()) {
			throw CardTraderException.invalidResponse(null);
		}

		JsonNode arrayWrapper = root.get("array");
		JsonNode dataWrapper = root.get("data");
		boolean hasArrayWrapper = arrayWrapper != null;
		boolean hasDataWrapper = dataWrapper != null;
		if (hasArrayWrapper == hasDataWrapper) {
			throw CardTraderException.invalidResponse(null);
		}

		JsonNode wrappedArray = hasArrayWrapper ? arrayWrapper : dataWrapper;
		if (!wrappedArray.isArray()) {
			throw CardTraderException.invalidResponse(null);
		}
		return wrappedArray;
	}

	private JsonNode readJson(String responseBody) {
		if (!StringUtils.hasText(responseBody)) {
			throw CardTraderException.invalidResponse(null);
		}
		try {
			JsonNode root = objectMapper.readTree(responseBody);
			if (root == null) {
				throw CardTraderException.invalidResponse(null);
			}
			return root;
		}
		catch (CardTraderException exception) {
			throw exception;
		}
		catch (JacksonException exception) {
			throw CardTraderException.invalidResponse(exception);
		}
	}

	private static void requireObject(JsonNode node) {
		if (node == null || !node.isObject()) {
			throw CardTraderException.invalidResponse(null);
		}
	}

	private static long requiredPositiveLong(JsonNode object, String fieldName) {
		JsonNode value = object.get(fieldName);
		if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
			throw CardTraderException.invalidResponse(null);
		}
		return value.longValue();
	}

	private static long requiredNonNegativeLong(JsonNode object, String fieldName) {
		JsonNode value = object.get(fieldName);
		if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
			throw CardTraderException.invalidResponse(null);
		}
		return value.longValue();
	}

	private static int requiredNonNegativeInt(JsonNode object, String fieldName) {
		JsonNode value = object.get(fieldName);
		if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) {
			throw CardTraderException.invalidResponse(null);
		}
		return value.intValue();
	}

	private static boolean requiredBoolean(JsonNode object, String fieldName) {
		JsonNode value = object.get(fieldName);
		if (value == null || !value.isBoolean()) {
			throw CardTraderException.invalidResponse(null);
		}
		return value.booleanValue();
	}

	private static Boolean optionalBoolean(JsonNode object, String fieldName) {
		JsonNode value = object.get(fieldName);
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.isBoolean()) {
			throw CardTraderException.invalidResponse(null);
		}
		return value.booleanValue();
	}

	private static String requiredCurrency(JsonNode object, String fieldName) {
		String currency = requiredText(object, fieldName);
		if (!currency.matches("[A-Z]{3}")) {
			throw CardTraderException.invalidResponse(null);
		}
		return currency;
	}

	private static String requiredText(JsonNode object, String fieldName) {
		JsonNode value = object.get(fieldName);
		if (value == null || !value.isString() || !StringUtils.hasText(value.asString())) {
			throw CardTraderException.invalidResponse(null);
		}
		return value.asString().trim();
	}

	private static String optionalText(JsonNode object, String fieldName) {
		JsonNode value = object.get(fieldName);
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.isString()) {
			throw CardTraderException.invalidResponse(null);
		}
		String text = value.asString().trim();
		return text.isEmpty() ? null : text;
	}
}
