package com.example.cardmonitoring.telegram;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cardmonitoring.user.AppUser;
import com.example.cardmonitoring.user.AppUserRepository;

@Service
public class TelegramService {

	private static final Logger LOGGER = LoggerFactory.getLogger(TelegramService.class);
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	private static final int TOKEN_BYTES = 32;

	private final AppUserRepository appUserRepository;
	private final TelegramLinkRequestRepository linkRequestRepository;
	private final TelegramProperties properties;
	private final TelegramClient telegramClient;
	private final TelegramQrCodeService qrCodeService;

	public TelegramService(
			AppUserRepository appUserRepository,
			TelegramLinkRequestRepository linkRequestRepository,
			TelegramProperties properties,
			TelegramClient telegramClient,
			TelegramQrCodeService qrCodeService) {
		this.appUserRepository = appUserRepository;
		this.linkRequestRepository = linkRequestRepository;
		this.properties = properties;
		this.telegramClient = telegramClient;
		this.qrCodeService = qrCodeService;
	}

	@Transactional(readOnly = true)
	public TelegramStatusResponse status(long userId) {
		if (!properties.isEnabled()) {
			return TelegramStatusResponse.disabled();
		}
		return TelegramStatusResponse.from(requireUser(userId));
	}

	@Transactional
	public TelegramLinkResponse createLink(long userId) {
		requireEnabled();
		AppUser user = requireUser(userId);
		Instant now = Instant.now();
		Instant expiresAt = now.plus(15, ChronoUnit.MINUTES);
		String token = newToken();
		linkRequestRepository.save(new TelegramLinkRequest(user, token, now, expiresAt));
		String linkUrl = properties.botDeepLink(token);
		LOGGER.info("Created Telegram link request: ownerId={}, expiresAt={}", userId, expiresAt);
		return new TelegramLinkResponse(
				linkUrl,
				qrCodeService.createSvg(linkUrl),
				expiresAt,
				properties.getBotUsername());
	}

	@Transactional
	public TelegramStartResult handleStart(String token, String chatId, String telegramUsername) {
		requireEnabled();
		Instant now = Instant.now();
		if (token == null || token.isBlank()) {
			return TelegramStartResult.MISSING_TOKEN;
		}
		return linkRequestRepository.findByToken(token.trim())
				.map(request -> linkUser(request, chatId, telegramUsername, now))
				.orElse(TelegramStartResult.UNKNOWN_TOKEN);
	}

	@Transactional
	public void unlink(long userId) {
		AppUser user = requireUser(userId);
		user.unlinkTelegram();
	}

	@Transactional
	public void sendTestMessage(long userId) {
		requireEnabled();
		AppUser user = requireUser(userId);
		if (user.getTelegramChatId() == null || !user.isTelegramEnabled()) {
			throw new TelegramException("Telegram is not linked for this user");
		}
		sendMessageToUser(user, "✅ Card Monitor è collegato correttamente. Da qui riceverai il riepilogo settimanale.");
	}

	@Transactional
	public void sendMessageToUser(long userId, String message) {
		requireEnabled();
		sendMessageToUser(requireUser(userId), message);
	}

	private void sendMessageToUser(AppUser user, String message) {
		if (user.getTelegramChatId() == null || !user.isTelegramEnabled()) {
			return;
		}
		try {
			telegramClient.sendMessage(user.getTelegramChatId(), message);
			user.recordTelegramSuccess();
		}
		catch (RuntimeException exception) {
			String safeMessage = exception.getMessage() == null ? "Telegram delivery failed" : exception.getMessage();
			user.recordTelegramError(safeMessage);
			LOGGER.warn("Telegram message delivery failed: ownerId={}, error={}", user.getId(), safeMessage);
		}
	}

	private TelegramStartResult linkUser(
			TelegramLinkRequest request,
			String chatId,
			String telegramUsername,
			Instant now) {
		if (request.isUsed()) {
			return TelegramStartResult.ALREADY_USED;
		}
		if (request.isExpired(now)) {
			return TelegramStartResult.EXPIRED_TOKEN;
		}
		if (chatId == null || chatId.isBlank()) {
			return TelegramStartResult.MISSING_CHAT;
		}
		AppUser user = request.getUser();
		user.linkTelegram(chatId, telegramUsername, now);
		request.markUsed(now);
		LOGGER.info("Telegram account linked: ownerId={}, telegramUsernamePresent={}",
				user.getId(), telegramUsername != null && !telegramUsername.isBlank());
		return TelegramStartResult.LINKED;
	}

	private void requireEnabled() {
		if (!properties.isEnabled()) {
			throw new TelegramException("Telegram integration is disabled");
		}
	}

	private AppUser requireUser(long userId) {
		return appUserRepository.findById(userId)
				.orElseThrow(() -> new IllegalStateException("Authenticated user no longer exists"));
	}

	private static String newToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		SECURE_RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public enum TelegramStartResult {
		LINKED,
		MISSING_TOKEN,
		UNKNOWN_TOKEN,
		EXPIRED_TOKEN,
		ALREADY_USED,
		MISSING_CHAT
	}
}
