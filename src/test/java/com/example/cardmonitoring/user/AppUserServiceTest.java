package com.example.cardmonitoring.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AppUserServiceTest {

	@Autowired
	private AppUserService appUserService;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void normalizesUsernameAndPersistsOnlyPasswordHash() {
		AppUserPrincipal registered = appUserService.register("  Stefano.Test  ", "password-sicura");

		AppUser persisted = appUserRepository.findById(registered.userId()).orElseThrow();
		assertThat(persisted.getUsername()).isEqualTo("stefano.test");
		assertThat(persisted.getPasswordHash()).isNotEqualTo("password-sicura");
		assertThat(passwordEncoder.matches("password-sicura", persisted.getPasswordHash())).isTrue();
	}

	@Test
	void rejectsDuplicateUsernameAndWeakPassword() {
		appUserService.register("stefano", "password-sicura");

		assertThatThrownBy(() -> appUserService.register("STEFANO", "password-diversa"))
				.isInstanceOf(UsernameAlreadyExistsException.class);
		assertThatThrownBy(() -> appUserService.register("mario", "breve"))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
