package com.example.cardmonitoring.security;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.example.cardmonitoring.user.AppUserPrincipal;

@Service
public class AdminAccessService {

	private final String administratorUsername;

	public AdminAccessService(@Value("${cardmonitoring.admin.username}") String administratorUsername) {
		this.administratorUsername = normalize(administratorUsername);
	}

	public boolean isAdministrator(Authentication authentication) {
		return authentication != null
				&& authentication.getPrincipal() instanceof AppUserPrincipal principal
				&& principal.isEnabled()
				&& administratorUsername.equals(normalize(principal.username()));
	}

	public void requireAdministrator(Authentication authentication) {
		if (!isAdministrator(authentication)) {
			throw new AccessDeniedException("Administrator access is required");
		}
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}
}
