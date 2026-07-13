package com.example.cardmonitoring.telegram;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramLinkRequestRepository extends JpaRepository<TelegramLinkRequest, Long> {

	Optional<TelegramLinkRequest> findByToken(String token);

	long deleteByUserId(long userId);
}
