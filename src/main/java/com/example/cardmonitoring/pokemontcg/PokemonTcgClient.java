package com.example.cardmonitoring.pokemontcg;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

@Component
public class PokemonTcgClient {

	private static final Logger LOGGER = LoggerFactory.getLogger(PokemonTcgClient.class);
	private static final int MAX_ATTEMPTS = 2;
	private static final long RETRY_DELAY_MILLIS = 350L;

	private final RestClient restClient;
	private final PokemonTcgProperties properties;
	private final PokemonTcgResponseParser responseParser;

	public PokemonTcgClient(
			@Qualifier("pokemonTcgRestClient") RestClient restClient,
			PokemonTcgProperties properties,
			PokemonTcgResponseParser responseParser) {
		this.restClient = restClient;
		this.properties = properties;
		this.responseParser = responseParser;
	}

	public List<PokemonTcgCardCandidate> searchCards(String query) {
		if (!StringUtils.hasText(query)) {
			LOGGER.info("Pokemon TCG card search skipped: blank query");
			return List.of();
		}
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				LOGGER.info("Calling Pokemon TCG cards API: query={}, attempt={}", query, attempt);
				String responseBody = get(uriBuilder -> uriBuilder
						.path("/cards")
						.queryParam("q", query)
						.queryParam("pageSize", 20)
						.queryParam("select", "id,name,number,set,images")
						.build());
				List<PokemonTcgCardCandidate> cards = responseParser.parseCards(responseBody);
				LOGGER.info("Pokemon TCG cards API completed: query={}, parsedCandidates={}, attempt={}",
						query, cards.size(), attempt);
				return cards;
			}
			catch (RestClientException exception) {
				if (attempt < MAX_ATTEMPTS) {
					LOGGER.warn("Pokemon TCG cards API failed, retrying: query={}, attempt={}, errorType={}, message={}",
							query, attempt, exception.getClass().getSimpleName(), exception.getMessage());
					pauseBeforeRetry();
					continue;
				}
				LOGGER.warn("Pokemon TCG cards API failed: query={}, attempts={}, errorType={}, message={}",
						query, attempt, exception.getClass().getSimpleName(), exception.getMessage());
				return List.of();
			}
			catch (IllegalArgumentException exception) {
				LOGGER.warn("Pokemon TCG cards API response rejected: query={}, errorType={}, message={}",
						query, exception.getClass().getSimpleName(), exception.getMessage());
				return List.of();
			}
		}
		return List.of();
	}

	public Optional<PokemonTcgCardCandidate> findCardById(String cardId) {
		if (!StringUtils.hasText(cardId)) {
			LOGGER.info("Pokemon TCG single-card lookup skipped: blank cardId");
			return Optional.empty();
		}
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				LOGGER.info("Calling Pokemon TCG single-card API: cardId={}, attempt={}", cardId, attempt);
				String responseBody = get(uriBuilder -> uriBuilder
						.path("/cards/{id}")
						.queryParam("select", "id,name,number,set,images")
						.build(cardId));
				Optional<PokemonTcgCardCandidate> card = responseParser.parseCard(responseBody);
				LOGGER.info("Pokemon TCG single-card API completed: cardId={}, found={}, attempt={}",
						cardId, card.isPresent(), attempt);
				return card;
			}
			catch (RestClientException exception) {
				if (attempt < MAX_ATTEMPTS) {
					LOGGER.warn("Pokemon TCG single-card API failed, retrying: cardId={}, attempt={}, errorType={}, message={}",
							cardId, attempt, exception.getClass().getSimpleName(), exception.getMessage());
					pauseBeforeRetry();
					continue;
				}
				LOGGER.warn("Pokemon TCG single-card API failed: cardId={}, attempts={}, errorType={}, message={}",
						cardId, attempt, exception.getClass().getSimpleName(), exception.getMessage());
				return Optional.empty();
			}
			catch (IllegalArgumentException exception) {
				LOGGER.warn("Pokemon TCG single-card API response rejected: cardId={}, errorType={}, message={}",
						cardId, exception.getClass().getSimpleName(), exception.getMessage());
				return Optional.empty();
			}
		}
		return Optional.empty();
	}

	private static void pauseBeforeRetry() {
		try {
			Thread.sleep(RETRY_DELAY_MILLIS);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}

	private String get(Function<UriBuilder, URI> uriFunction) {
		RestClient.RequestHeadersSpec<?> request = restClient.get()
				.uri(uriFunction)
				.accept(MediaType.APPLICATION_JSON);
		if (StringUtils.hasText(properties.getApiKey())) {
			request = request.header("X-Api-Key", properties.getApiKey());
		}
		return request
				.retrieve()
				.onStatus(HttpStatusCode::isError, (httpRequest, response) -> {
					throw new RestClientException("Pokemon TCG API returned HTTP " + response.getStatusCode().value());
				})
				.body(String.class);
	}
}
