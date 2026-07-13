package com.example.cardmonitoring.monitoring;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.cardmonitoring.common.ApiExceptionHandler;
import com.example.cardmonitoring.pricing.ConfidenceLevel;
import com.example.cardmonitoring.user.AppUserPrincipal;

@ExtendWith(MockitoExtension.class)
class MonitoringControllerTest {

	@Mock
	private MonitoringService monitoringService;

	@Mock
	private MonitoringExportService monitoringExportService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders
				.standaloneSetup(new MonitoringController(monitoringService, monitoringExportService))
				.setControllerAdvice(new ApiExceptionHandler())
				.build();
	}

	@Test
	void createsMonitoringAndReturnsInitialObservation() throws Exception {
		when(monitoringService.create(42L, new CreateMonitoringRequest(
				1472, 111151, "it", "Near Mint", false, false, false, null, null, false, false)))
				.thenReturn(new CreatedMonitoringResponse(monitoringResponse(), observationResponse()));

		mockMvc.perform(post("/api/monitorings")
				.principal(authentication())
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
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/monitorings/7"))
				.andExpect(jsonPath("$.monitoring.id").value(7))
				.andExpect(jsonPath("$.monitoring.cardName").value("Charizard"))
				.andExpect(jsonPath("$.initialObservation.averagePriceCents").value(10100.00))
				.andExpect(jsonPath("$.initialObservation.confidence").value("MEDIUM"));
	}

	@Test
	void exposesListDetailRefreshHistoryAndDeactivateEndpoints() throws Exception {
		MonitoringResponse monitoring = monitoringResponse();
		PriceObservationResponse observation = observationResponse();
		when(monitoringService.findActive(42L)).thenReturn(List.of(monitoring));
		when(monitoringService.findById(42L, 7L)).thenReturn(monitoring);
		when(monitoringService.refresh(42L, 7L)).thenReturn(observation);
		when(monitoringService.findObservations(42L, 7L)).thenReturn(List.of(observation));
		when(monitoringService.updatePurchasePrice(42L, 7L, new UpdatePurchasePriceRequest(12_345L)))
				.thenReturn(monitoringResponse(12_345L));

		mockMvc.perform(get("/api/monitorings").principal(authentication()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(7));
		mockMvc.perform(get("/api/monitorings/7").principal(authentication()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.blueprintId").value(111151));
		mockMvc.perform(post("/api/monitorings/7/refresh").principal(authentication()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.usedOffers").value(3));
		mockMvc.perform(get("/api/monitorings/7/observations").principal(authentication()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].currency").value("EUR"));
		mockMvc.perform(put("/api/monitorings/7/purchase-price")
				.principal(authentication())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "purchasePriceCents": 12345
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.purchasePriceCents").value(12_345));
		mockMvc.perform(delete("/api/monitorings/7").principal(authentication()))
				.andExpect(status().isNoContent());

		verify(monitoringService).deactivate(42L, 7L);
	}

	@Test
	void exportsActiveMonitoringsAsAttachment() throws Exception {
		when(monitoringExportService.export(42L, MonitoringExportService.ExportFormat.CSV))
				.thenReturn(new MonitoringExportService.ExportedFile(
						"card-monitor-export.csv",
						"text/csv;charset=UTF-8",
						"monitoraggio_id\n".getBytes()));

		mockMvc.perform(get("/api/monitorings/export")
				.param("format", "csv")
				.principal(authentication()))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition",
						"attachment; filename=\"card-monitor-export.csv\""))
				.andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"));
	}

	@Test
	void returnsUniformProblemsForMonitoringErrorsAndMalformedJson() throws Exception {
		when(monitoringService.findById(42L, 99L)).thenThrow(new MonitoringNotFoundException(99L));
		when(monitoringService.findById(42L, 100L)).thenThrow(new IllegalStateException("technical detail"));
		when(monitoringService.refresh(42L, 8L)).thenThrow(new MonitoringInactiveException(8L));
		when(monitoringService.refresh(42L, 9L)).thenThrow(new MonitoringRefreshInProgressException(9L));

		mockMvc.perform(get("/api/monitorings/99").principal(authentication()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("MONITORING_NOT_FOUND"));
		mockMvc.perform(post("/api/monitorings/8/refresh").principal(authentication()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("MONITORING_INACTIVE"));
		mockMvc.perform(post("/api/monitorings/9/refresh").principal(authentication()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("MONITORING_REFRESH_IN_PROGRESS"));
		mockMvc.perform(get("/api/monitorings/100").principal(authentication()))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
		mockMvc.perform(post("/api/monitorings")
				.principal(authentication())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{invalid"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	private static Authentication authentication() {
		AppUserPrincipal principal = new AppUserPrincipal(42L, "stefano", "hash", true);
		return UsernamePasswordAuthenticationToken.authenticated(
				principal, null, principal.getAuthorities());
	}

	private static MonitoringResponse monitoringResponse() {
		return monitoringResponse(null);
	}

	private static MonitoringResponse monitoringResponse(Long purchasePriceCents) {
		return new MonitoringResponse(
				7L,
				111151,
				1472,
				"Charizard",
				"Holo Rare | 4/102",
				"Base Set",
				"bs",
				null,
				null,
				null,
				"it",
				"Near Mint",
				false,
				false,
				false,
				null,
				null,
				false,
				false,
				true,
				"EUR",
				purchasePriceCents,
				Instant.parse("2026-07-03T09:59:00Z"),
				Instant.parse("2026-07-03T10:00:00Z"),
				null);
	}

	private static PriceObservationResponse observationResponse() {
		return new PriceObservationResponse(
				11L,
				Instant.parse("2026-07-03T10:00:00Z"),
				"EUR",
				new BigDecimal("10100.00"),
				10_000L,
				10_200L,
				3,
				3,
				ConfidenceLevel.MEDIUM);
	}
}
