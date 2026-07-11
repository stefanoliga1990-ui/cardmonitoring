package com.example.cardmonitoring.pokemontcg;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class PokemonTcgResponseParser {

	private static final Logger LOGGER = LoggerFactory.getLogger(PokemonTcgResponseParser.class);

	private final ObjectMapper objectMapper;

	PokemonTcgResponseParser(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	List<PokemonTcgCardCandidate> parseCards(String responseBody) {
		try {
			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode data = root.get("data");
			if (data == null || !data.isArray()) {
				LOGGER.warn("Pokemon TCG response ignored: missing or non-array data field");
				return List.of();
			}
			List<PokemonTcgCardCandidate> cards = new ArrayList<>();
			int skipped = 0;
			for (JsonNode item : data) {
				Optional<PokemonTcgCardCandidate> candidate = parseCandidate(item);
				if (candidate.isPresent()) {
					cards.add(candidate.get());
				}
				else {
					skipped++;
				}
			}
			if (skipped > 0) {
				LOGGER.info("Pokemon TCG response parsed with skipped incomplete candidate(s): parsed={}, skipped={}",
						cards.size(), skipped);
			}
			return List.copyOf(cards);
		}
		catch (RuntimeException exception) {
			LOGGER.warn("Pokemon TCG response parsing failed: errorType={}, message={}",
					exception.getClass().getSimpleName(), exception.getMessage());
			return List.of();
		}
	}

	Optional<PokemonTcgCardCandidate> parseCard(String responseBody) {
		try {
			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode data = root.get("data");
			if (data == null || !data.isObject()) {
				LOGGER.warn("Pokemon TCG single-card response ignored: missing or non-object data field");
				return Optional.empty();
			}
			Optional<PokemonTcgCardCandidate> candidate = parseCandidate(data);
			if (candidate.isEmpty()) {
				LOGGER.warn("Pokemon TCG single-card response ignored: incomplete card data");
			}
			return candidate;
		}
		catch (RuntimeException exception) {
			LOGGER.warn("Pokemon TCG single-card response parsing failed: errorType={}, message={}",
					exception.getClass().getSimpleName(), exception.getMessage());
			return Optional.empty();
		}
	}

	private static Optional<PokemonTcgCardCandidate> parseCandidate(JsonNode item) {
		String id = optionalText(item, "id");
		String name = optionalText(item, "name");
		String number = optionalText(item, "number");
		JsonNode set = item.get("set");
		String setId = optionalText(set, "id");
		String setName = optionalText(set, "name");
		String setSeries = optionalText(set, "series");
		Integer setPrintedTotal = optionalInteger(set, "printedTotal");
		Integer setTotal = optionalInteger(set, "total");
		String setReleaseDate = optionalText(set, "releaseDate");
		JsonNode images = item.get("images");
		String smallImage = optionalText(images, "small");
		String largeImage = optionalText(images, "large");
		if (id != null && name != null && number != null && (smallImage != null || largeImage != null)) {
			return Optional.of(new PokemonTcgCardCandidate(
					id, name, number, setId, setName, setSeries, setPrintedTotal, setTotal, setReleaseDate,
					smallImage, largeImage));
		}
		return Optional.empty();
	}

	private static String optionalText(JsonNode object, String fieldName) {
		if (object == null || !object.isObject()) {
			return null;
		}
		JsonNode value = object.get(fieldName);
		if (value == null || !value.isTextual() || value.asText().isBlank()) {
			return null;
		}
		return value.asText().trim();
	}

	private static Integer optionalInteger(JsonNode object, String fieldName) {
		if (object == null || !object.isObject()) {
			return null;
		}
		JsonNode value = object.get(fieldName);
		if (value == null || !value.isIntegralNumber()) {
			return null;
		}
		return value.asInt();
	}
}
