package com.example.cardmonitoring.collection;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.cardmonitoring.catalog.CatalogCard;
import com.example.cardmonitoring.pokemontcg.CardImageService;

import jakarta.annotation.PreDestroy;

@Service
public class CollectionImageSyncService {

	private static final Logger LOGGER = LoggerFactory.getLogger(CollectionImageSyncService.class);
	private static final long REQUEST_PAUSE_MILLIS = 350L;

	private final CollectionSetRepository collectionSetRepository;
	private final CollectionCardRepository collectionCardRepository;
	private final CardImageService cardImageService;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "collection-image-sync");
		thread.setDaemon(true);
		return thread;
	});
	private final Set<Long> runningCollectionSets = ConcurrentHashMap.newKeySet();

	public CollectionImageSyncService(
			CollectionSetRepository collectionSetRepository,
			CollectionCardRepository collectionCardRepository,
			CardImageService cardImageService) {
		this.collectionSetRepository = collectionSetRepository;
		this.collectionCardRepository = collectionCardRepository;
		this.cardImageService = cardImageService;
	}

	public void start(long collectionSetId) {
		if (!runningCollectionSets.add(collectionSetId)) {
			LOGGER.info("Collection image sync already running: collectionSetId={}", collectionSetId);
			return;
		}
		executor.submit(() -> run(collectionSetId));
	}

	private void run(long collectionSetId) {
		try {
			CollectionSet collectionSet = collectionSetRepository.findById(collectionSetId)
					.orElse(null);
			if (collectionSet == null) {
				LOGGER.info("Collection image sync skipped: collection set not found. collectionSetId={}", collectionSetId);
				return;
			}
			if (collectionSet.getImageSyncStatus() == CollectionImageSyncStatus.COMPLETED) {
				LOGGER.info("Collection image sync skipped: collection set already completed. collectionSetId={}",
						collectionSetId);
				return;
			}

			Instant startedAt = Instant.now();
			collectionSet.markSyncStarted(startedAt);
			collectionSetRepository.save(collectionSet);
			List<CollectionCard> cards = collectionCardRepository.findByCollectionSetIdOrderBySortOrderAsc(collectionSetId);
			LOGGER.info("Collection image sync started: collectionSetId={}, expansionId={}, cards={}",
					collectionSetId, collectionSet.getExpansionId(), cards.size());

			int failures = 0;
			for (CollectionCard card : cards) {
				try {
					cardImageService.resolve(new CatalogCard(
							card.getBlueprintId(),
							card.getCardName(),
							card.getCardVersion(),
							card.getExpansionId(),
							collectionSet.getName(),
							collectionSet.getCode()));
				}
				catch (RuntimeException exception) {
					failures += 1;
					LOGGER.warn(
							"Collection image sync failed for card: collectionSetId={}, blueprintId={}, cardName='{}', errorType={}, message={}",
							collectionSetId, card.getBlueprintId(), card.getCardName(),
							exception.getClass().getSimpleName(), exception.getMessage());
				}
				pauseBetweenRequests();
			}

			Instant completedAt = Instant.now();
			if (failures == 0) {
				collectionSet.markSyncCompleted(completedAt);
			}
			else {
				collectionSet.markSyncFailed(completedAt,
						"Sincronizzazione immagini completata con " + failures + " errori.");
			}
			collectionSetRepository.save(collectionSet);
			LOGGER.info("Collection image sync completed: collectionSetId={}, failures={}", collectionSetId, failures);
		}
		finally {
			runningCollectionSets.remove(collectionSetId);
		}
	}

	private static void pauseBetweenRequests() {
		try {
			Thread.sleep(REQUEST_PAUSE_MILLIS);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}

	@PreDestroy
	void stop() {
		executor.shutdownNow();
	}
}
