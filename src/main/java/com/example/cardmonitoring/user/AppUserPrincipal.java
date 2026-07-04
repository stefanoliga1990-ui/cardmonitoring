package com.example.cardmonitoring.user;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record AppUserPrincipal(
		long userId,
		String username,
		String password,
		boolean enabled) implements UserDetails {

	private static final List<GrantedAuthority> AUTHORITIES = List.of(
			new SimpleGrantedAuthority("ROLE_USER"));

	public static AppUserPrincipal from(AppUser user) {
		return new AppUserPrincipal(
				user.getId(), user.getUsername(), user.getPasswordHash(), user.isEnabled());
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return AUTHORITIES;
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public String getUsername() {
		return username;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}
}
