package com.example.cardmonitoring.security;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cardmonitoring.user.AppUserPrincipal;
import com.example.cardmonitoring.user.AppUserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AppUserService appUserService;
	private final AuthenticationManager authenticationManager;
	private final SecurityContextRepository securityContextRepository;
	private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

	public AuthController(
			AppUserService appUserService,
			AuthenticationManager authenticationManager,
			SecurityContextRepository securityContextRepository,
			SessionAuthenticationStrategy sessionAuthenticationStrategy) {
		this.appUserService = appUserService;
		this.authenticationManager = authenticationManager;
		this.securityContextRepository = securityContextRepository;
		this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
	}

	@PostMapping("/register")
	public ResponseEntity<AuthenticatedUserResponse> register(
			@RequestBody RegisterRequest request,
			HttpServletRequest servletRequest,
			HttpServletResponse servletResponse) {
		if (request == null) {
			throw new IllegalArgumentException("request is required");
		}
		AppUserPrincipal registered = appUserService.register(request.username(), request.password());
		Authentication authentication = authenticate(
				registered.username(), request.password(), servletRequest, servletResponse);
		return ResponseEntity.status(201).body(currentUser(authentication));
	}

	@PostMapping("/login")
	public AuthenticatedUserResponse login(
			@RequestBody LoginRequest request,
			HttpServletRequest servletRequest,
			HttpServletResponse servletResponse) {
		if (request == null) {
			throw new IllegalArgumentException("request is required");
		}
		Authentication authentication = authenticate(
				request.username(), request.password(), servletRequest, servletResponse);
		return currentUser(authentication);
	}

	@GetMapping("/me")
	public AuthenticatedUserResponse me(Authentication authentication) {
		return currentUser(authentication);
	}

	@GetMapping("/csrf")
	public CsrfTokenResponse csrf(HttpServletRequest request) {
		CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
		if (token == null) {
			throw new IllegalStateException("CSRF token is unavailable");
		}
		return new CsrfTokenResponse(token.getHeaderName(), token.getToken());
	}

	private Authentication authenticate(
			String username,
			String password,
			HttpServletRequest request,
			HttpServletResponse response) {
		Authentication authentication = authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(username, password));
		sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);
		return authentication;
	}

	private static AuthenticatedUserResponse currentUser(Authentication authentication) {
		if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
			throw new IllegalStateException("Authenticated user is unavailable");
		}
		return AuthenticatedUserResponse.from(principal);
	}
}
