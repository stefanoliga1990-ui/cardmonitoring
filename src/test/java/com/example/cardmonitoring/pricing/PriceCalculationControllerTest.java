package com.example.cardmonitoring.pricing;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.cardmonitoring.common.ApiExceptionHandler;

@ExtendWith(MockitoExtension.class)
class PriceCalculationControllerTest {

	@Mock
	private PriceCalculationPreviewService previewService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders
				.standaloneSetup(new PriceCalculationController(previewService))
				.setControllerAdvice(new ApiExceptionHandler())
				.build();
	}

	@Test
	void exposesDetailedNonPersistedPriceCalculation() throws Exception {
		PriceCalculationRequest request = new PriceCalculationRequest(
				1472, 111151, "it", "Near Mint", false, false, false, false, false);
		when(previewService.calculate(request)).thenReturn(new PriceCalculationPreviewResponse(
				Instant.parse("2026-07-03T10:00:00Z"),
				"CARDTRADER_ACTIVE_LISTINGS",
				1472,
				"Base Set",
				"bs",
				111151,
				"Charizard",
				"Holo Rare | 4/102",
				"https://images.test/small.png",
				"https://images.test/large.png",
				"POKEMON_TCG_API",
				"it",
				"Near Mint",
				false, false, false, false, false,
				"EUR",
				new BigDecimal("10100.00"),
				10_000L,
				10_200L,
				3,
				3,
				ConfidenceLevel.MEDIUM,
				List.of(new UsedMarketplaceOffer(418408517L, 10_000L, "EUR", 1))));

		mockMvc.perform(post("/api/price-calculations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "expansionId": 1472,
						  "blueprintId": 111151,
						  "language": "it",
						  "condition": "Near Mint",
						  "firstEdition": false,
						  "reverse": false,
						  "graded": false,
						  "signed": false,
						  "altered": false
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.source").value("CARDTRADER_ACTIVE_LISTINGS"))
				.andExpect(jsonPath("$.cardName").value("Charizard"))
				.andExpect(jsonPath("$.imageUrlSmall").value("https://images.test/small.png"))
				.andExpect(jsonPath("$.averagePriceCents").value(10100.00))
				.andExpect(jsonPath("$.offersUsed[0].offerId").value(418408517L))
				.andExpect(jsonPath("$.offersUsed[0].priceCents").value(10000));
	}
}
