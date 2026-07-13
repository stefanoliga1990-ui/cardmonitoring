package com.example.cardmonitoring.security;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cardmonitoring.user.AppUserPrincipal;
import com.example.cardmonitoring.user.AppUserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/account")
public class AccountController {

	private final AppUserService appUserService;
	private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

	public AccountController(AppUserService appUserService) {
		this.appUserService = appUserService;
	}

	@GetMapping
	public AccountProfileResponse profile(Authentication authentication) {
		return AccountProfileResponse.from(appUserService.profile(userId(authentication)));
	}

	@PutMapping("/password")
	public ResponseEntity<Void> changePassword(
			Authentication authentication,
			@RequestBody ChangePasswordRequest request,
			HttpServletRequest servletRequest,
			HttpServletResponse servletResponse) {
		if (request == null) {
			throw new IllegalArgumentException("request is required");
		}
		appUserService.changePassword(userId(authentication), request.currentPassword(), request.newPassword());
		logoutHandler.logout(servletRequest, servletResponse, authentication);
		SecurityContextHolder.clearContext();
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping
	public ResponseEntity<Void> deleteAccount(
			Authentication authentication,
			@RequestBody DeleteAccountRequest request,
			HttpServletRequest servletRequest,
			HttpServletResponse servletResponse) {
		if (request == null) {
			throw new IllegalArgumentException("request is required");
		}
		appUserService.deleteAccount(userId(authentication), request.currentPassword());
		logoutHandler.logout(servletRequest, servletResponse, authentication);
		SecurityContextHolder.clearContext();
		return ResponseEntity.noContent().build();
	}

	private static long userId(Authentication authentication) {
		if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
			throw new IllegalStateException("Authenticated user is unavailable");
		}
		return principal.userId();
	}
}
