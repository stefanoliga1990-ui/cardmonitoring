package com.example.cardmonitoring.tools;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.cardmonitoring.catalog.CatalogBlueprint;
import com.example.cardmonitoring.catalog.CatalogExpansion;
import com.example.cardmonitoring.catalog.CatalogService;
import com.example.cardmonitoring.pokemontcg.CardImageRepository;
import com.example.cardmonitoring.pokemontcg.StoredCardImage;

/** Performs a read-only comparison between the curated CardTrader catalogue and the image cache. */
@Service
public class ImageCoverageAuditService {

	private static final Logger LOGGER = LoggerFactory.getLogger(ImageCoverageAuditService.class);
	private static final String IMAGE_SOURCE = "POKEMON_TCG_API";

	private final ExecutorService executorService;
	private final CatalogService catalogService;
	private final CardImageRepository cardImageRepository;
	private final Object lock = new Object();
	private MutableStatus status = MutableStatus.idle();

	public ImageCoverageAuditService(
			ExecutorService imageBackfillExecutor,
			CatalogService catalogService,
			CardImageRepository cardImageRepository) {
		this.executorService = imageBackfillExecutor;
		this.catalogService = catalogService;
		this.cardImageRepository = cardImageRepository;
	}

	public ImageCoverageAuditStatusResponse start() {
		synchronized (lock) {
			if (status.running) {
				return status.toResponse();
			}
			status = MutableStatus.running();
			executorService.submit(this::run);
			return status.toResponse();
		}
	}

	public ImageCoverageAuditStatusResponse status() {
		synchronized (lock) {
			return status.toResponse();
		}
	}

	private void run() {
		try {
			List<CatalogExpansion> expansions = catalogService.getPokemonExpansions();
			update(snapshot -> snapshot.totalExpansions = expansions.size());
			for (CatalogExpansion expansion : expansions) {
				update(snapshot -> snapshot.currentExpansion = expansion.name());
				try {
					inspectExpansion(expansion);
				}
				catch (RuntimeException exception) {
					LOGGER.warn("Image coverage audit skipped expansion: expansionId={}, name='{}', error={}",
							expansion.id(), expansion.name(), exception.getMessage());
					update(snapshot -> {
						snapshot.failedExpansions += 1;
						snapshot.lastError = "Set " + expansion.name() + ": " + safeMessage(exception);
					});
				}
				finally {
					update(snapshot -> snapshot.processedExpansions += 1);
				}
			}
			update(snapshot -> {
				snapshot.running = false;
				snapshot.finishedAt = Instant.now();
				snapshot.currentExpansion = null;
			});
		}
		catch (RuntimeException exception) {
			LOGGER.error("Image coverage audit failed", exception);
			update(snapshot -> {
				snapshot.running = false;
				snapshot.finishedAt = Instant.now();
				snapshot.currentExpansion = null;
				snapshot.lastError = safeMessage(exception);
			});
		}
	}

	private void inspectExpansion(CatalogExpansion expansion) {
		List<CatalogBlueprint> blueprints = catalogService.getPokemonBlueprints(expansion.id());
		Set<Long> blueprintsWithImage = new HashSet<>();
		for (StoredCardImage image : cardImageRepository.findByExpansionIdAndImageSource(expansion.id(), IMAGE_SOURCE)) {
			if (image.hasImage()) {
				blueprintsWithImage.add(image.getBlueprintId());
			}
		}
		int imagesAvailable = (int) blueprints.stream()
				.map(CatalogBlueprint::id)
				.filter(blueprintsWithImage::contains)
				.count();
		int missingImages = blueprints.size() - imagesAvailable;
		if (missingImages > 0) {
			update(snapshot -> snapshot.incompleteExpansions.add(new IncompleteImageExpansionResponse(
					expansion.id(), expansion.name(), expansion.code(), blueprints.size(), imagesAvailable, missingImages)));
		}
	}

	private void update(java.util.function.Consumer<MutableStatus> update) {
		synchronized (lock) {
			update.accept(status);
		}
	}

	private static String safeMessage(Exception exception) {
		String message = exception.getMessage();
		return message == null || message.isBlank() ? "Errore non specificato" : message;
	}

	private static final class MutableStatus {
		private boolean running;
		private Instant startedAt;
		private Instant finishedAt;
		private int processedExpansions;
		private int totalExpansions;
		private String currentExpansion;
		private int failedExpansions;
		private String lastError;
		private final List<IncompleteImageExpansionResponse> incompleteExpansions = new ArrayList<>();

		private static MutableStatus idle() {
			return new MutableStatus();
		}

		private static MutableStatus running() {
			MutableStatus status = new MutableStatus();
			status.running = true;
			status.startedAt = Instant.now();
			return status;
		}

		private ImageCoverageAuditStatusResponse toResponse() {
			return new ImageCoverageAuditStatusResponse(
					running, startedAt, finishedAt, processedExpansions, totalExpansions, currentExpansion,
					failedExpansions, lastError, List.copyOf(incompleteExpansions));
		}
	}
}
