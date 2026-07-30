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
		return searchCards(query, 20);
	}

	public List<PokemonTcgCardCandidate> searchCards(String query, int pageSize) {
		if (!StringUtils.hasText(query)) {
			LOGGER.info("Pokemon TCG card search skipped: blank query");
			return List.of();
		}
		int safePageSize = Math.max(1, Math.min(pageSize, 250));
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				LOGGER.info("Calling Pokemon TCG cards API: query={}, pageSize={}, attempt={}",
						query, safePageSize, attempt);
				String responseBody = get(uriBuilder -> uriBuilder
						.path("/cards")
						.queryParam("q", query)
						.queryParam("pageSize", safePageSize)
						.queryParam("select", "id,name,number,set,images")
						.build());
				List<PokemonTcgCardCandidate> cards = responseParser.parseCards(responseBody);
				LOGGER.info("Pokemon TCG cards API completed: query={}, parsedCandidates={}, pageSize={}, attempt={}",
						query, cards.size(), safePageSize, attempt);
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

	/**
	 * Returns a result only when the API confirms that the query identifies exactly one card.
	 * This is deliberately used for the last-resort name-only lookup, where choosing a wrong
	 * image would be worse than leaving the placeholder visible.
	 */
	public Optional<PokemonTcgCardCandidate> searchSingleCard(String query) {
		if (!StringUtils.hasText(query)) return Optional.empty();
		PokemonTcgCardPage page = getCardsPage(query, 1, 2);
		return page.totalCount() == 1 && page.cards().size() == 1
				? Optional.of(page.cards().get(0))
				: Optional.empty();
	}

	public PokemonTcgCardPage getCardsPage(int page, int pageSize) {
		return getCardsPage(null, page, pageSize);
	}

	public List<PokemonTcgCardCandidate> getCardsForSet(String setId) {
		if (!StringUtils.hasText(setId)) return List.of();
		List<PokemonTcgCardCandidate> cards = new java.util.ArrayList<>();
		int page = 1;
		while (true) {
			PokemonTcgCardPage result = getCardsPage("set.id:" + setId, page, 250);
			cards.addAll(result.cards());
			if (result.cards().isEmpty() || cards.size() >= result.totalCount() || result.cards().size() < 250) return List.copyOf(cards);
			page++;
		}
	}

	public List<PokemonTcgSetCandidate> getSets() {
		try {
			String body = get(uriBuilder -> uriBuilder.path("/sets").queryParam("pageSize", 250)
					.queryParam("select", "id,name,ptcgoCode").build());
			return responseParser.parseSets(body);
		}
		catch (RestClientException | IllegalArgumentException exception) {
			LOGGER.warn("Pokemon TCG set API failed: {}", exception.getMessage());
			return List.of();
		}
	}

	private PokemonTcgCardPage getCardsPage(String query, int page, int pageSize) {
		int safePage = Math.max(1, page);
		int safePageSize = Math.max(1, Math.min(pageSize, 250));
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				LOGGER.info("Calling Pokemon TCG cards API page: page={}, pageSize={}, attempt={}",
						safePage, safePageSize, attempt);
				String responseBody = get(uriBuilder -> uriBuilder
						.path("/cards")
						.queryParamIfPresent("q", java.util.Optional.ofNullable(query))
						.queryParam("page", safePage)
						.queryParam("pageSize", safePageSize)
						.queryParam("select", "id,name,number,set,images")
						.build());
				PokemonTcgCardPage cardPage = responseParser.parseCardPage(responseBody);
				LOGGER.info(
						"Pokemon TCG cards API page completed: page={}, parsedCandidates={}, count={}, totalCount={}, attempt={}",
						cardPage.page(), cardPage.cards().size(), cardPage.count(), cardPage.totalCount(), attempt);
				return cardPage;
			}
			catch (RestClientException exception) {
				if (attempt < MAX_ATTEMPTS) {
					LOGGER.warn(
							"Pokemon TCG cards API page failed, retrying: page={}, attempt={}, errorType={}, message={}",
							safePage, attempt, exception.getClass().getSimpleName(), exception.getMessage());
					pauseBeforeRetry();
					continue;
				}
				LOGGER.warn("Pokemon TCG cards API page failed: page={}, attempts={}, errorType={}, message={}",
						safePage, attempt, exception.getClass().getSimpleName(), exception.getMessage());
				return new PokemonTcgCardPage(List.of(), safePage, safePageSize, 0, 0);
			}
			catch (IllegalArgumentException exception) {
				LOGGER.warn("Pokemon TCG cards API page response rejected: page={}, errorType={}, message={}",
						safePage, exception.getClass().getSimpleName(), exception.getMessage());
				return new PokemonTcgCardPage(List.of(), safePage, safePageSize, 0, 0);
			}
		}
		return new PokemonTcgCardPage(List.of(), safePage, safePageSize, 0, 0);
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
