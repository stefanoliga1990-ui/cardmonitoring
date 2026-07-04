package com.example.cardmonitoring.user;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserService implements UserDetailsService {

	private static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]{3,50}");
	private static final int MINIMUM_PASSWORD_LENGTH = 8;
	private static final int MAXIMUM_BCRYPT_PASSWORD_BYTES = 72;

	private final AppUserRepository appUserRepository;
	private final PasswordEncoder passwordEncoder;

	public AppUserService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
		this.appUserRepository = appUserRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public AppUserPrincipal register(String username, String password) {
		String normalizedUsername = normalizeUsername(username);
		validatePassword(password);
		if (appUserRepository.existsByUsername(normalizedUsername)) {
			throw new UsernameAlreadyExistsException();
		}
		try {
			AppUser user = appUserRepository.saveAndFlush(
					new AppUser(normalizedUsername, passwordEncoder.encode(password)));
			return AppUserPrincipal.from(user);
		}
		catch (DataIntegrityViolationException exception) {
			throw new UsernameAlreadyExistsException();
		}
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		String normalizedUsername;
		try {
			normalizedUsername = normalizeUsername(username);
		}
		catch (IllegalArgumentException exception) {
			throw new UsernameNotFoundException("User not found");
		}
		return appUserRepository.findByUsername(normalizedUsername)
				.map(AppUserPrincipal::from)
				.orElseThrow(() -> new UsernameNotFoundException("User not found"));
	}

	public static String normalizeUsername(String username) {
		String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
		if (!USERNAME.matcher(normalized).matches() || normalized.startsWith("__")) {
			throw new IllegalArgumentException(
					"username must contain 3 to 50 letters, numbers, dots, hyphens or underscores");
		}
		return normalized;
	}

	private static void validatePassword(String password) {
		if (password == null || password.length() < MINIMUM_PASSWORD_LENGTH) {
			throw new IllegalArgumentException("password must contain at least 8 characters");
		}
		if (password.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_BCRYPT_PASSWORD_BYTES) {
			throw new IllegalArgumentException("password is too long");
		}
	}
}
