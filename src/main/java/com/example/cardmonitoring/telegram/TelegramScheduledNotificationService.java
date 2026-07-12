package com.example.cardmonitoring.telegram;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.cardmonitoring.monitoring.MonitoringService.ScheduledRefreshResult;
import com.example.cardmonitoring.pricing.ConfidenceLevel;

@Service
public class TelegramScheduledNotificationService {

	private static final ZoneId ROME = ZoneId.of("Europe/Rome");
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ITALIAN)
			.withZone(ROME);

	private final TelegramProperties properties;
	private final TelegramService telegramService;

	public TelegramScheduledNotificationService(TelegramProperties properties, TelegramService telegramService) {
		this.properties = properties;
		this.telegramService = telegramService;
	}

	public void notifyScheduledRun(List<ScheduledRefreshResult> results) {
		if (!properties.isEnabled() || results.isEmpty()) {
			return;
		}
		Map<Long, List<ScheduledRefreshResult>> byOwner = new LinkedHashMap<>();
		for (ScheduledRefreshResult result : results) {
			byOwner.computeIfAbsent(result.target().ownerId(), ignored -> new ArrayList<>()).add(result);
		}
		byOwner.forEach((ownerId, ownerResults) ->
				telegramService.sendMessageToUser(ownerId, weeklyMessage(ownerResults)));
	}

	private static String weeklyMessage(List<ScheduledRefreshResult> results) {
		StringBuilder message = new StringBuilder();
		message.append("📈 Riepilogo settimanale Card Monitor\n");
		message.append("Media delle quattro offerte compatibili più economiche disponibili su CardTrader.\n");
		for (ScheduledRefreshResult result : results) {
			message.append("\n");
			appendResult(message, result);
		}
		return message.toString();
	}

	private static void appendResult(StringBuilder message, ScheduledRefreshResult result) {
		var target = result.target();
		message.append("• ")
				.append(target.cardName())
				.append(" — ")
				.append(target.expansionName())
				.append(" · ")
				.append(target.expansionCode())
				.append("\n");
		message.append("  ").append(target.cardVersion()).append("\n");
		List<String> criteria = new ArrayList<>();
		criteria.add(languageLabel(target.language()));
		if (target.condition() != null) {
			criteria.add(target.condition());
		}
		criteria.add(variantsLabel(result));
		message.append("  Criteri: ")
				.append(String.join(", ", criteria))
				.append("\n");

		if (!result.success()) {
			message.append("  Aggiornamento non riuscito: ").append(result.error()).append("\n");
			return;
		}

		var observation = result.observation();
		if (observation.averagePriceCents() == null) {
			message.append("  Nessuna offerta compatibile trovata.\n");
			return;
		}
		message.append("  Prezzo medio: ")
				.append(formatCents(observation.averagePriceCents(), observation.currency()))
				.append("\n");
		message.append("  Campione: ")
				.append(observation.usedOffers())
				.append(" su ")
				.append(observation.compatibleOffers())
				.append(" offerte compatibili")
				.append(" · Attendibilità: ")
				.append(confidenceLabel(observation.confidence()))
				.append("\n");
		message.append("  Aggiornato: ").append(DATE_TIME.format(observation.observedAt())).append("\n");
	}

	private static String variantsLabel(ScheduledRefreshResult result) {
		var target = result.target();
		List<String> variants = new ArrayList<>();
		if (target.firstEdition()) {
			variants.add("Prima edizione");
		}
		if (target.reverse()) {
			variants.add("Reverse");
		}
		if (target.graded()) {
			if (target.gradingCompany() != null && target.gradingGrade() != null) {
				variants.add("Gradata " + gradingCompanyLabel(target.gradingCompany()) + " " + target.gradingGrade());
			}
			else {
				variants.add("Gradata");
			}
		}
		if (target.signed()) {
			variants.add("Firmata");
		}
		if (target.altered()) {
			variants.add("Alterata");
		}
		return variants.isEmpty() ? "Standard" : String.join(", ", variants);
	}

	private static String formatCents(BigDecimal cents, String currency) {
		BigDecimal amount = cents.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
		NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.ITALY);
		formatter.setCurrency(java.util.Currency.getInstance(currency));
		return formatter.format(amount);
	}

	private static String confidenceLabel(ConfidenceLevel confidence) {
		return switch (confidence) {
			case HIGH -> "Alta";
			case MEDIUM -> "Media";
			case LOW -> "Bassa";
			case NO_DATA -> "Nessun dato";
		};
	}

	private static String languageLabel(String code) {
		return switch (code) {
			case "it" -> "Italiano";
			case "en" -> "Inglese";
			case "fr" -> "Francese";
			case "de" -> "Tedesco";
			case "es" -> "Spagnolo";
			case "pt" -> "Portoghese";
			case "nl" -> "Olandese";
			case "ru" -> "Russo";
			case "pl" -> "Polacco";
			case "sv" -> "Svedese";
			case "kr" -> "Coreano";
			case "jp" -> "Giapponese";
			case "zh-CN" -> "Cinese semplificato";
			case "zh-TW" -> "Cinese tradizionale";
			case "id" -> "Indonesiano";
			case "th" -> "Thailandese";
			default -> code;
		};
	}

	private static String gradingCompanyLabel(String company) {
		return switch (company) {
			case "EUROPEAN_GRADING" -> "European Grading";
			default -> company;
		};
	}
}
