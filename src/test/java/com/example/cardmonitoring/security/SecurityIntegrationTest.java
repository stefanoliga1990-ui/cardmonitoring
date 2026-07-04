package com.example.cardmonitoring.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.example.cardmonitoring.common.JsonRequestSizeLimitFilter;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SecurityIntegrationTest {

	@Autowired
	private WebApplicationContext applicationContext;

	@Autowired
	private JsonRequestSizeLimitFilter jsonRequestSizeLimitFilter;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
				.addFilters(jsonRequestSizeLimitFilter)
				.apply(springSecurity())
				.build();
	}

	@Test
	void protectsPrivateApisButKeepsLoginPageAndCsrfEndpointPublic() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/auth/csrf"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty());
		mockMvc.perform(get("/api/monitorings"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void registersLogsInAndRequiresCsrfForLogout() throws Exception {
		MvcResult registration = mockMvc.perform(post("/api/auth/register")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username":"stefano","password":"password-sicura"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.username").value("stefano"))
				.andReturn();
		MockHttpSession session = (MockHttpSession) registration.getRequest().getSession(false);

		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("stefano"));
		mockMvc.perform(post("/api/auth/logout").session(session))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
				.andExpect(status().isNoContent());

		MvcResult login = mockMvc.perform(post("/api/auth/login")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username":"stefano","password":"password-sicura"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("stefano"))
				.andReturn();
		MockHttpSession loginSession = (MockHttpSession) login.getRequest().getSession(false);
		mockMvc.perform(get("/api/auth/me").session(loginSession))
				.andExpect(status().isOk());
	}

	@Test
	void returnsUniformErrorForInvalidCredentials() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username":"missing","password":"password-errata"}
						"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void rejectsOversizedJsonRequestsBeforeDeserialization() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("x".repeat(65 * 1024)))
				.andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.code").value("REQUEST_TOO_LARGE"));
	}
}
