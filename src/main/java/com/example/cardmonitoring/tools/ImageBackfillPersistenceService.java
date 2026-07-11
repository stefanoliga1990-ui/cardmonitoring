package com.example.cardmonitoring.tools;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cardmonitoring.catalog.CatalogCard;
import com.example.cardmonitoring.pokemontcg.CardImage;
import com.example.cardmonitoring.pokemontcg.CardImageRepository;
import com.example.cardmonitoring.pokemontcg.StoredCardImage;

@Service
class ImageBackfillPersistenceService {

	private static final Duration IMAGE_URL_MAXIMUM_AGE = Duration.ofDays(120);
	private static final String SOURCE = "POKEMON_TCG_API";

	private final CardImageRepository cardImageRepository;

	ImageBackfillPersistenceService(CardImageRepository cardImageRepository) {
		this.cardImageRepository = cardImageRepository;
	}

	@Transactional
	SaveResult saveOrRefresh(CatalogCard card, String collectorNumber, CardImage image, Instant now) {
		return cardImageRepository
				.findByExpansionIdAndBlueprintIdAndCollectorNumberAndImageSource(
						card.expansionId(), card.blueprintId(), collectorNumber, SOURCE)
				.map(storedImage -> refreshExisting(card, collectorNumber, image, now, storedImage))
				.orElseGet(() -> saveNew(card, collectorNumber, image, now));
	}

	private SaveResult refreshExisting(
			CatalogCard card,
			String collectorNumber,
			CardImage image,
			Instant now,
			StoredCardImage storedImage) {
		if (storedImage.isFresh(now, IMAGE_URL_MAXIMUM_AGE)) {
			storedImage.recordUse(now);
			cardImageRepository.save(storedImage);
			return SaveResult.ALREADY_PRESENT;
		}
		storedImage.refresh(card, collectorNumber, image, now);
		cardImageRepository.save(storedImage);
		return SaveResult.UPDATED;
	}

	private SaveResult saveNew(CatalogCard card, String collectorNumber, CardImage image, Instant now) {
		cardImageRepository.save(new StoredCardImage(card, collectorNumber, image, now));
		return SaveResult.SAVED;
	}

	enum SaveResult {
		SAVED,
		UPDATED,
		ALREADY_PRESENT
	}
}
