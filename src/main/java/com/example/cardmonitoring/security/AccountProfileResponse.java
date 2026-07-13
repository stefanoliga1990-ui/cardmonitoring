package com.example.cardmonitoring.security;

import java.time.Instant;

import com.example.cardmonitoring.user.AppUser;

public record AccountProfileResponse(long id, String username, Instant createdAt) {

	static AccountProfileResponse from(AppUser user) {
		return new AccountProfileResponse(user.getId(), user.getUsername(), user.getCreatedAt());
	}
}
