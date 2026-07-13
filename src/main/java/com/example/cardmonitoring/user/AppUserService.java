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

import com.example.cardmonitoring.monitoring.MonitoringRepository;
import com.example.cardmonitoring.telegram.TelegramLinkRequestRepository;

@Service
public class AppUserService implements UserDetailsService {

	private static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]{3,50}");
	private static final int MINIMUM_PASSWORD_LENGTH = 8;
	private static final int MAXIMUM_BCRYPT_PASSWORD_BYTES = 72;

	private final AppUserRepository appUserRepository;
	private final PasswordEncoder passwordEncoder;
	private final MonitoringRepository monitoringRepository;
	private final TelegramLinkRequestRepository telegramLinkRequestRepository;

	public AppUserService(
			AppUserRepository appUserRepository,
			PasswordEncoder passwordEncoder,
			MonitoringRepository monitoringRepository,
			TelegramLinkRequestRepository telegramLinkRequestRepository) {
		this.appUserRepository = appUserRepository;
		this.passwordEncoder = passwordEncoder;
		this.monitoringRepository = monitoringRepository;
		this.telegramLinkRequestRepository = telegramLinkRequestRepository;
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

	@Transactional(readOnly = true)
	public AppUser profile(long userId) {
		return requireUser(userId);
	}

	@Transactional
	public void changePassword(long userId, String currentPassword, String newPassword) {
		AppUser user = requireUser(userId);
		requireMatchingPassword(user, currentPassword);
		validatePassword(newPassword);
		user.changePasswordHash(passwordEncoder.encode(newPassword));
	}

	@Transactional
	public void deleteAccount(long userId, String currentPassword) {
		AppUser user = requireUser(userId);
		requireMatchingPassword(user, currentPassword);
		telegramLinkRequestRepository.deleteByUserId(userId);
		monitoringRepository.deleteByOwnerId(userId);
		appUserRepository.delete(user);
	}

	public static String normalizeUsername(String username) {
		String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
		if (!USERNAME.matcher(normalized).matches() || normalized.startsWith("__")) {
			throw new IllegalArgumentException(
					"username must contain 3 to 50 letters, numbers, dots, hyphens or underscores");
		}
		return normalized;
	}

	private AppUser requireUser(long userId) {
		return appUserRepository.findById(userId)
				.orElseThrow(() -> new UsernameNotFoundException("User not found"));
	}

	private void requireMatchingPassword(AppUser user, String password) {
		if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
			throw new IllegalArgumentException("La password attuale non è corretta");
		}
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
