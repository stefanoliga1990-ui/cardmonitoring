package com.example.cardmonitoring.security;

import com.example.cardmonitoring.user.AppUserPrincipal;

public record AuthenticatedUserResponse(long id, String username) {

	static AuthenticatedUserResponse from(AppUserPrincipal principal) {
		return new AuthenticatedUserResponse(principal.userId(), principal.username());
	}
}
