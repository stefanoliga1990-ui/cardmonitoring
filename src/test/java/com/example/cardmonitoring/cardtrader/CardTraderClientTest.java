package com.example.cardmonitoring.cardtrader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.ObjectMapper;

class CardTraderClientTest {

	private static final String TOKEN = "test-token-that-must-not-leak";

	private CardTraderProperties properties;
	private MockRestServiceServer server;
	private CardTraderClient client;

	@BeforeEach
	void setUp() {
		properties = new CardTraderProperties();
		properties.setBaseUrl(URI.create("https://api.cardtrader.test/api/v2"));
		properties.setToken(TOKEN);
		properties.setConnectTimeout(Duration.ofSeconds(1));
		properties.setReadTimeout(Duration.ofSeconds(1));

		RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl().toString());
		server = MockRestServiceServer.bindTo(builder).build();
		client = new CardTraderClient(
				builder.build(), new CardTraderResponseParser(new ObjectMapper()), properties);
	}

	@AfterEach
	void verifyRequests() {
		server.verify();
	}

	@Test
	void getInfoUsesBearerTokenAndReturnsOnlySafeFields() {
		server.expect(requestTo("https://api.cardtrader.test/api/v2/info"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
				.andRespond(withSuccess("""
						{
						  "id": 3,
						  "name": "Test App",
						  "shared_secret": "must-not-be-exposed",
						  "user_id": 42
						}
						""", MediaType.APPLICATION_JSON));

		CardTraderInfo info = client.getInfo();

		assertThat(info).isEqualTo(new CardTraderInfo(3, "Test App"));
		assertThat(info.toString()).doesNotContain("must-not-be-exposed");
	}

	@Test
	void getCategoriesUsesPokemonGameQueryParameter() {
		server.expect(requestTo("https://api.cardtrader.test/api/v2/categories?game_id=5"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
				.andRespond(withSuccess("""
						{"array":[{"id":73,"name":"Pokémon Singles","game_id":5}]}
						""", MediaType.APPLICATION_JSON));

		assertThat(client.getCategories(5))
				.containsExactly(new CardTraderCategory(73, "Pokémon Singles", 5));
	}

	@Test
	void getBlueprintsUsesExpansionQueryParameter() {
		server.expect(requestTo("https://api.cardtrader.test/api/v2/blueprints/export?expansion_id=1472"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("""
						[{"id":111151,"name":"Charizard","version":"Holo Rare | 4/102",
						  "category_id":73,"expansion_id":1472}]
						""", MediaType.APPLICATION_JSON));

		assertThat(client.getBlueprints(1472)).containsExactly(
				new CardTraderBlueprint(111151, "Charizard", "Holo Rare | 4/102", 73, 1472));
	}

	@Test
	void getMarketplaceProductsUsesBlueprintAndLanguageQueryParameters() {
		server.expect(requestTo(
				"https://api.cardtrader.test/api/v2/marketplace/products?blueprint_id=111151&language=it"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
				.andRespond(withSuccess("""
						{"111151":[{
						  "id":418408517,"blueprint_id":111151,"name_en":"Charizard",
						  "expansion":{"id":1472,"code":"bs","name_en":"Base Set"},
						  "quantity":1,"properties_hash":{"pokemon_language":"it"},
						  "graded":false,"on_vacation":false,
						  "price":{"cents":8064,"currency":"EUR"}
						}]}
						""", MediaType.APPLICATION_JSON));

		assertThat(client.getMarketplaceProducts(111151, "it"))
				.singleElement()
				.satisfies(offer -> {
					assertThat(offer.id()).isEqualTo(418408517);
					assertThat(offer.price()).isEqualTo(new CardTraderMoney(8064, "EUR"));
					assertThat(offer.properties().language()).isEqualTo("it");
				});
	}

	@Test
	void getMarketplaceProductsRejectsInvalidLanguageWithoutMakingARequest() {
		assertThatThrownBy(() -> client.getMarketplaceProducts(111151, "italian"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void getInfoRejectsMissingTokenWithoutMakingARequest() {
		properties.setToken("  ");

		assertThatThrownBy(client::getInfo)
				.isInstanceOfSatisfying(CardTraderException.class, exception -> {
					assertThat(exception.getReason()).isEqualTo(CardTraderException.Reason.CONFIGURATION);
					assertThat(exception.getMessage()).doesNotContain(TOKEN);
				});
	}

	@Test
	void getInfoMapsUnauthorizedResponseWithoutLeakingToken() {
		server.expect(requestTo("https://api.cardtrader.test/api/v2/info"))
				.andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		assertThatThrownBy(client::getInfo)
				.isInstanceOfSatisfying(CardTraderException.class, exception -> {
					assertThat(exception.getReason()).isEqualTo(CardTraderException.Reason.AUTHENTICATION);
					assertThat(exception.getHttpStatus()).isEqualTo(401);
					assertThat(exception.getMessage()).doesNotContain(TOKEN);
				});
	}

	@Test
	void getInfoMapsRateLimitResponse() {
		server.expect(requestTo("https://api.cardtrader.test/api/v2/info"))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

		assertThatThrownBy(client::getInfo)
				.isInstanceOfSatisfying(CardTraderException.class, exception -> {
					assertThat(exception.getReason()).isEqualTo(CardTraderException.Reason.RATE_LIMIT);
					assertThat(exception.getHttpStatus()).isEqualTo(429);
				});
	}

	@Test
	void getInfoMapsServerError() {
		server.expect(requestTo("https://api.cardtrader.test/api/v2/info"))
				.andRespond(withServerError());

		assertThatThrownBy(client::getInfo)
				.isInstanceOfSatisfying(CardTraderException.class, exception -> {
					assertThat(exception.getReason()).isEqualTo(CardTraderException.Reason.REMOTE_UNAVAILABLE);
					assertThat(exception.getHttpStatus()).isEqualTo(500);
				});
	}

	@Test
	void getInfoRejectsUnexpectedJsonStructure() {
		server.expect(requestTo("https://api.cardtrader.test/api/v2/info"))
				.andRespond(withSuccess("[{\"id\":3,\"name\":\"Test App\"}]", MediaType.APPLICATION_JSON));

		assertThatThrownBy(client::getInfo)
				.isInstanceOfSatisfying(CardTraderException.class, exception ->
						assertThat(exception.getReason()).isEqualTo(CardTraderException.Reason.INVALID_RESPONSE));
	}

	@Test
	void getInfoRejectsMalformedJson() {
		server.expect(requestTo("https://api.cardtrader.test/api/v2/info"))
				.andRespond(withSuccess("{", MediaType.APPLICATION_JSON));

		assertThatThrownBy(client::getInfo)
				.isInstanceOfSatisfying(CardTraderException.class, exception ->
						assertThat(exception.getReason()).isEqualTo(CardTraderException.Reason.INVALID_RESPONSE));
	}

	@Test
	void transportTimeoutIsMappedExplicitly() {
		ResourceAccessException timeout = new ResourceAccessException(
				"request failed", new HttpTimeoutException("timed out"));

		CardTraderException mapped = CardTraderClient.mapTransportFailure(timeout);

		assertThat(mapped.getReason()).isEqualTo(CardTraderException.Reason.TIMEOUT);
		assertThat(mapped.getMessage()).doesNotContain(TOKEN);
	}
}
