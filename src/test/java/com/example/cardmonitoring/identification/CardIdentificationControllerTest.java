package com.example.cardmonitoring.identification;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class CardIdentificationControllerTest {

	@Mock
	private CardIdentificationService cardIdentificationService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders
				.standaloneSetup(new CardIdentificationController(cardIdentificationService))
				.setControllerAdvice(new ApiExceptionHandler())
				.build();
	}

	@Test
	void exposesVisualCardCandidates() throws Exception {
		when(cardIdentificationService.findCandidates(new CardIdentificationRequest("Charizard", "4", "102")))
				.thenReturn(List.of(new CardIdentificationCandidate(
						"base1-4",
						"base1",
						"Charizard",
						"4",
						"4/102",
						"Base",
						"Base series",
						102,
						102,
						"1999/01/09",
						"https://images.test/small.png",
						"https://images.test/large.png",
						1472L,
						"Base Set",
						"bs",
						111151L,
						"Charizard",
						"Holo Rare | 4/102",
						"HIGH",
						true)));

		mockMvc.perform(post("/api/card-identification/candidates")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name": "Charizard",
						  "number": "4",
						  "total": "102"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].cardName").value("Charizard"))
				.andExpect(jsonPath("$[0].displayNumber").value("4/102"))
				.andExpect(jsonPath("$[0].imageUrlSmall").value("https://images.test/small.png"))
				.andExpect(jsonPath("$[0].cardTraderExpansionId").value(1472))
				.andExpect(jsonPath("$[0].cardTraderBlueprintId").value(111151))
				.andExpect(jsonPath("$[0].selectable").value(true));
	}
}
