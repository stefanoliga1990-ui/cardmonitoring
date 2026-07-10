package com.example.cardmonitoring.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.cardmonitoring.telegram.TelegramService.TelegramStartResult;
import com.example.cardmonitoring.user.AppUserPrincipal;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/telegram")
public class TelegramController {

	private static final Logger LOGGER = LoggerFactory.getLogger(TelegramController.class);

	private final TelegramService telegramService;
	private final TelegramClient telegramClient;
	private final TelegramProperties properties;

	public TelegramController(
			TelegramService telegramService,
			TelegramClient telegramClient,
			TelegramProperties properties) {
		this.telegramService = telegramService;
		this.telegramClient = telegramClient;
		this.properties = properties;
	}

	@GetMapping("/status")
	public TelegramStatusResponse status(@AuthenticationPrincipal AppUserPrincipal principal) {
		return telegramService.status(principal.userId());
	}

	@PostMapping("/link-requests")
	public TelegramLinkResponse createLink(@AuthenticationPrincipal AppUserPrincipal principal) {
		return telegramService.createLink(principal.userId());
	}

	@PostMapping("/test-message")
	public ResponseEntity<Void> sendTestMessage(@AuthenticationPrincipal AppUserPrincipal principal) {
		telegramService.sendTestMessage(principal.userId());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/unlink")
	public ResponseEntity<Void> unlink(@AuthenticationPrincipal AppUserPrincipal principal) {
		telegramService.unlink(principal.userId());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/webhook")
	public ResponseEntity<Void> webhook(
			@RequestHeader(name = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret,
			@RequestBody JsonNode update) {
		if (!properties.isEnabled()) {
			return ResponseEntity.noContent().build();
		}
		if (!properties.getWebhookSecret().equals(secret)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid Telegram webhook secret");
		}
		handleWebhookUpdate(update);
		return ResponseEntity.noContent().build();
	}

	private void handleWebhookUpdate(JsonNode update) {
		JsonNode message = update.get("message");
		if (message == null || !message.isObject()) {
			return;
		}
		JsonNode chat = message.get("chat");
		if (chat == null || !chat.isObject()) {
			return;
		}
		String chatType = text(chat.get("type"));
		if (!"private".equals(chatType)) {
			return;
		}

		String text = text(message.get("text"));
		String token = startToken(text);
		if (token == null) {
			return;
		}

		String chatId = chatId(chat.get("id"));
		JsonNode from = message.get("from");
		String username = from == null || !from.isObject() ? null : text(from.get("username"));
		TelegramStartResult result = telegramService.handleStart(token, chatId, username);
		LOGGER.info("Handled Telegram start command: result={}", result);
		sendStartReply(chatId, result);
	}

	private void sendStartReply(String chatId, TelegramStartResult result) {
		String message = switch (result) {
			case LINKED -> "✅ Telegram collegato a Card Monitor. Riceverai qui il riepilogo settimanale dei tuoi monitoraggi.";
			case EXPIRED_TOKEN -> "Questo QR code è scaduto. Torna nella dashboard e genera un nuovo collegamento Telegram.";
			case ALREADY_USED -> "Questo QR code è già stato usato. Se vuoi ricollegare Telegram, genera un nuovo QR dalla dashboard.";
			case UNKNOWN_TOKEN -> "Non riesco a riconoscere questo collegamento. Genera un nuovo QR dalla dashboard Card Monitor.";
			case MISSING_CHAT, MISSING_TOKEN -> "Collegamento Telegram incompleto. Genera un nuovo QR dalla dashboard Card Monitor.";
		};
		try {
			telegramClient.sendMessage(chatId, message);
		}
		catch (RuntimeException exception) {
			LOGGER.warn("Telegram start reply failed: result={}, error={}", result, exception.getMessage());
		}
	}

	private static String startToken(String text) {
		String normalized = text == null ? "" : text.trim();
		if (normalized.isEmpty()) {
			return null;
		}
		String[] parts = normalized.split("\\s+", 2);
		String command = parts[0];
		if (!"/start".equals(command) && !command.startsWith("/start@")) {
			return null;
		}
		return parts.length == 2 && !parts[1].isBlank() ? parts[1].trim() : "";
	}

	private static String text(JsonNode node) {
		return node != null && node.isString() ? node.asString().trim() : "";
	}

	private static String chatId(JsonNode node) {
		if (node == null || node.isNull()) {
			return "";
		}
		if (node.isIntegralNumber() && node.canConvertToLong()) {
			return Long.toString(node.longValue());
		}
		return node.isString() ? node.asString().trim() : "";
	}
}
