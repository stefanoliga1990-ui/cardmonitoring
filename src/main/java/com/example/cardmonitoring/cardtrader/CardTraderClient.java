package com.example.cardmonitoring.cardtrader;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

@Component
public class CardTraderClient {

	private static final Pattern LANGUAGE_CODE = Pattern.compile("[a-z]{2}(?:-[A-Z]{2})?");

	private final RestClient restClient;
	private final CardTraderResponseParser responseParser;
	private final CardTraderProperties properties;

	public CardTraderClient(RestClient restClient, CardTraderResponseParser responseParser,
			CardTraderProperties properties) {
		this.restClient = restClient;
		this.responseParser = responseParser;
		this.properties = properties;
	}

	public CardTraderInfo getInfo() {
		return responseParser.parseInfo(get(uriBuilder -> uriBuilder.path("/info").build()));
	}

	public List<CardTraderGame> getGames() {
		return responseParser.parseGames(get(uriBuilder -> uriBuilder.path("/games").build()));
	}

	public List<CardTraderCategory> getCategories(long gameId) {
		requirePositiveId(gameId, "gameId");
		return responseParser.parseCategories(get(uriBuilder -> uriBuilder
				.path("/categories")
				.queryParam("game_id", gameId)
				.build()));
	}

	public List<CardTraderExpansion> getExpansions() {
		return responseParser.parseExpansions(get(uriBuilder -> uriBuilder.path("/expansions").build()));
	}

	public List<CardTraderBlueprint> getBlueprints(long expansionId) {
		requirePositiveId(expansionId, "expansionId");
		return responseParser.parseBlueprints(get(uriBuilder -> uriBuilder
				.path("/blueprints/export")
				.queryParam("expansion_id", expansionId)
				.build()));
	}

	public List<CardTraderMarketplaceOffer> getMarketplaceProducts(long blueprintId, String language) {
		requirePositiveId(blueprintId, "blueprintId");
		String normalizedLanguage = requireLanguage(language);
		return responseParser.parseMarketplaceProducts(get(uriBuilder -> uriBuilder
				.path("/marketplace/products")
				.queryParam("blueprint_id", blueprintId)
				.queryParam("language", normalizedLanguage)
				.build()), blueprintId);
	}

	private String get(Function<UriBuilder, URI> uriFunction) {
		String token = properties.requireToken();
		try {
			return restClient.get()
					.uri(uriFunction)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
					.accept(MediaType.APPLICATION_JSON)
					.retrieve()
					.onStatus(HttpStatusCode::isError, (request, response) -> {
						throw CardTraderException.forHttpStatus(response.getStatusCode().value());
					})
					.body(String.class);
		}
		catch (CardTraderException exception) {
			throw exception;
		}
		catch (ResourceAccessException exception) {
			throw mapTransportFailure(exception);
		}
		catch (RestClientException exception) {
			throw CardTraderException.unavailable(exception);
		}
	}

	private static void requirePositiveId(long id, String parameterName) {
		if (id <= 0) {
			throw new IllegalArgumentException(parameterName + " must be positive");
		}
	}

	private static String requireLanguage(String language) {
		String normalizedLanguage = language == null ? "" : language.trim();
		if (!LANGUAGE_CODE.matcher(normalizedLanguage).matches()) {
			throw new IllegalArgumentException("language must be a CardTrader language code");
		}
		return normalizedLanguage;
	}

	static CardTraderException mapTransportFailure(ResourceAccessException exception) {
		Throwable cause = exception;
		while (cause != null) {
			if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
				return CardTraderException.timeout(exception);
			}
			cause = cause.getCause();
		}
		return CardTraderException.unavailable(exception);
	}
}
