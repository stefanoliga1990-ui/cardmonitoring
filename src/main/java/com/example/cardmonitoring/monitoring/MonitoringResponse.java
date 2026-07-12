package com.example.cardmonitoring.monitoring;

import java.time.Instant;

public record MonitoringResponse(
		long id,
		long blueprintId,
		long expansionId,
		String cardName,
		String cardVersion,
		String expansionName,
		String expansionCode,
		String imageUrlSmall,
		String imageUrlLarge,
		String imageSource,
		String language,
		String condition,
		boolean firstEdition,
		boolean reverse,
		boolean graded,
		String gradingCompany,
		String gradingGrade,
		boolean signed,
		boolean altered,
		boolean active,
		String currency,
		Long purchasePriceCents,
		Instant createdAt,
		Instant lastCheckedAt,
		String lastError) {

	static MonitoringResponse from(Monitoring monitoring) {
		return new MonitoringResponse(
				monitoring.getId(),
				monitoring.getBlueprintId(),
				monitoring.getExpansionId(),
				monitoring.getCardName(),
				monitoring.getCardVersion(),
				monitoring.getExpansionName(),
				monitoring.getExpansionCode(),
				monitoring.getImageUrlSmall(),
				monitoring.getImageUrlLarge(),
				monitoring.getImageSource(),
				monitoring.getLanguage(),
				monitoring.getCondition(),
				monitoring.isFirstEdition(),
				monitoring.isReverse(),
				monitoring.isGraded(),
				monitoring.getGradingCompany(),
				monitoring.getGradingGrade(),
				monitoring.isSigned(),
				monitoring.isAltered(),
				monitoring.isActive(),
				monitoring.getCurrency(),
				monitoring.getPurchasePriceCents(),
				monitoring.getCreatedAt(),
				monitoring.getLastCheckedAt(),
				monitoring.getLastError());
	}
}
