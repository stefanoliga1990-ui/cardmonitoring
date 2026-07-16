package com.example.cardmonitoring.tools;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.cardmonitoring.catalog.CatalogBlueprint;
import com.example.cardmonitoring.catalog.CatalogCard;
import com.example.cardmonitoring.catalog.CatalogExpansion;
import com.example.cardmonitoring.catalog.CatalogService;
import com.example.cardmonitoring.pokemontcg.CardImage;
import com.example.cardmonitoring.pokemontcg.CardImageRepository;
import com.example.cardmonitoring.pokemontcg.CardImageService;
import com.example.cardmonitoring.pokemontcg.StoredCardImage;

@Service
public class ImageBackfillService {

	private static final Logger LOGGER = LoggerFactory.getLogger(ImageBackfillService.class);
	private static final String SOURCE = "POKEMON_TCG_API";
	private static final Pattern COLLECTOR_NUMBER_PATTERN = Pattern.compile(
			"(?i)(?:^|\\s|\\|)([a-z]*\\d+[a-z]*)\\s*(?:/\\s*([a-z]*\\d+[a-z]*))?");

	private final ImageBackfillProperties properties;
	private final ExecutorService executorService;
	private final CatalogService catalogService;
	private final CardImageService cardImageService;
	private final CardImageRepository cardImageRepository;
	private final Object lock = new Object();

	private volatile boolean stopRequested;
	private MutableStatus status = MutableStatus.disabled();

	public ImageBackfillService(
			ImageBackfillProperties properties,
			ExecutorService imageBackfillExecutor,
			CatalogService catalogService,
			CardImageService cardImageService,
			CardImageRepository cardImageRepository) {
		this.properties = properties;
		this.executorService = imageBackfillExecutor;
		this.catalogService = catalogService;
		this.cardImageService = cardImageService;
		this.cardImageRepository = cardImageRepository;
	}

	public ImageBackfillStatusResponse start() {
		if (!properties.isEnabled()) {
			return disabledStatus();
		}
		synchronized (lock) {
			if (status.state == ImageBackfillRunState.RUNNING || status.state == ImageBackfillRunState.STOPPING) {
				return status.toResponse(true);
			}
			stopRequested = false;
			status = MutableStatus.running();
			executorService.submit(this::run);
			return status.toResponse(true);
		}
	}

	public ImageBackfillStatusResponse stop() {
		if (!properties.isEnabled()) {
			return disabledStatus();
		}
		synchronized (lock) {
			if (status.state == ImageBackfillRunState.RUNNING) {
				stopRequested = true;
				status.state = ImageBackfillRunState.STOPPING;
				status.lastError = null;
			}
			return status.toResponse(true);
		}
	}

	public ImageBackfillStatusResponse status() {
		if (!properties.isEnabled()) {
			return disabledStatus();
		}
		synchronized (lock) {
			if (status.state == ImageBackfillRunState.DISABLED) {
				status = MutableStatus.idle();
			}
			return status.toResponse(true);
		}
	}

	private ImageBackfillStatusResponse disabledStatus() {
		synchronized (lock) {
			stopRequested = true;
			status = MutableStatus.disabled();
			return status.toResponse(false);
		}
	}

	private void run() {
		try {
			LOGGER.info("Slow image backfill started");
			List<CatalogExpansion> expansions = catalogService.getPokemonExpansions();
			update(snapshot -> snapshot.totalExpansions = expansions.size());

			for (CatalogExpansion expansion : expansions) {
				if (shouldStop()) {
					markStopped();
					return;
				}
				processExpansion(expansion);
			}

			synchronized (lock) {
				status.state = ImageBackfillRunState.COMPLETED;
				status.finishedAt = Instant.now();
				status.currentCard = null;
			}
			LOGGER.info("Slow image backfill completed: {}", status());
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			if (stopRequested) {
				markStopped();
			}
			else {
				fail("Image backfill interrupted");
			}
		}
		catch (RuntimeException exception) {
			LOGGER.error("Slow image backfill failed", exception);
			fail(safeMessage(exception));
		}
	}

	private void processExpansion(CatalogExpansion expansion) throws InterruptedException {
		LOGGER.info("Slow image backfill processing expansion: expansionId={}, name='{}'",
				expansion.id(), expansion.name());
		update(snapshot -> {
			snapshot.currentExpansion = expansion.name();
			snapshot.currentCard = null;
			snapshot.currentExpansionTotalBlueprints = 0;
			snapshot.currentExpansionProcessedBlueprints = 0;
			snapshot.currentExpansionImagesAlreadyPresent = 0;
			snapshot.currentExpansionSavedImages = 0;
			snapshot.currentExpansionNotFound = 0;
			snapshot.currentExpansionSkippedWithoutCollectorNumber = 0;
			snapshot.currentExpansionErrors = 0;
		});

		List<CatalogBlueprint> blueprints;
		try {
			blueprints = catalogService.getPokemonBlueprints(expansion.id());
		}
		catch (RuntimeException exception) {
			LOGGER.warn("Slow image backfill expansion skipped: expansionId={}, name='{}', error={}",
					expansion.id(), expansion.name(), exception.getMessage());
			update(snapshot -> {
				snapshot.errors++;
				snapshot.lastError = "Set " + expansion.name() + ": " + safeMessage(exception);
				snapshot.processedExpansions++;
				snapshot.expansionResults.add(new ImageBackfillExpansionResultResponse(
						expansion.id(),
						expansion.name(),
						expansion.code(),
						0,
						0,
						0,
						0,
						0,
						1,
						0));
			});
			return;
		}

		Set<ImageKey> existingImages = existingImageKeys(expansion.id());
		update(snapshot -> {
			snapshot.totalBlueprints += blueprints.size();
			snapshot.currentExpansionTotalBlueprints = blueprints.size();
			snapshot.currentExpansionImagesAlreadyPresent = existingImages.size();
			snapshot.alreadyPresentImages += existingImages.size();
		});

		for (CatalogBlueprint blueprint : blueprints) {
			if (shouldStop()) {
				finishCurrentExpansion(expansion, blueprints.size(), existingImages.size());
				markStopped();
				return;
			}
			processBlueprint(expansion, blueprint, existingImages);
		}

		finishCurrentExpansion(expansion, blueprints.size(), existingImages.size());
		update(snapshot -> snapshot.processedExpansions++);
	}

	private void processBlueprint(
			CatalogExpansion expansion,
			CatalogBlueprint blueprint,
			Set<ImageKey> existingImages) throws InterruptedException {
		CatalogCard card = new CatalogCard(
				blueprint.id(),
				blueprint.name(),
				blueprint.version(),
				expansion.id(),
				expansion.name(),
				expansion.code());
		update(snapshot -> snapshot.currentCard = blueprint.name());

		Optional<String> collectorNumber = collectorNumber(blueprint.version()).map(ImageBackfillService::normalizeNumber);
		if (collectorNumber.isEmpty()) {
			update(snapshot -> {
				snapshot.skippedWithoutCollectorNumber++;
				snapshot.currentExpansionSkippedWithoutCollectorNumber++;
				recordProcessedBlueprint(snapshot);
			});
			return;
		}

		ImageKey imageKey = new ImageKey(blueprint.id(), collectorNumber.get());
		if (existingImages.contains(imageKey)) {
			update(this::recordProcessedBlueprint);
			return;
		}

		ResolutionResult result = resolveWithAttempts(card);
		if (result.found()) {
			existingImages.add(imageKey);
			update(snapshot -> {
				snapshot.savedImages++;
				snapshot.currentExpansionSavedImages++;
				recordProcessedBlueprint(snapshot);
			});
		}
		else if (result.errorMessage() == null) {
			update(snapshot -> {
				snapshot.skippedWithoutPokemonCandidate++;
				snapshot.currentExpansionNotFound++;
				recordProcessedBlueprint(snapshot);
			});
		}
		else {
			LOGGER.warn("Slow image backfill card failed: expansionId={}, blueprintId={}, cardName='{}', error={}",
					expansion.id(), blueprint.id(), blueprint.name(), result.errorMessage());
			update(snapshot -> {
				snapshot.errors++;
				snapshot.currentExpansionErrors++;
				snapshot.lastError = blueprint.name() + ": " + result.errorMessage();
				recordProcessedBlueprint(snapshot);
			});
		}

		pauseBetweenCards();
	}

	private ResolutionResult resolveWithAttempts(CatalogCard card) throws InterruptedException {
		String lastError = null;
		for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
			if (shouldStop()) {
				throw new InterruptedException("Image backfill stopped");
			}
			try {
				LOGGER.info(
						"Slow image backfill resolving image: blueprintId={}, expansionId={}, cardName='{}', attempt={}/{}",
						card.blueprintId(), card.expansionId(), card.cardName(), attempt, properties.getMaxAttempts());
				Optional<CardImage> image = cardImageService.resolve(card);
				if (image.isPresent() && image.get().hasImage()) {
					return ResolutionResult.imageFound();
				}
				lastError = null;
			}
			catch (RuntimeException exception) {
				lastError = safeMessage(exception);
				LOGGER.warn(
						"Slow image backfill resolve attempt failed: blueprintId={}, expansionId={}, attempt={}, error={}",
						card.blueprintId(), card.expansionId(), attempt, lastError);
			}
			if (attempt < properties.getMaxAttempts()) {
				pauseBetweenRetries();
			}
		}
		return lastError == null ? ResolutionResult.notFound() : ResolutionResult.error(lastError);
	}

	private Set<ImageKey> existingImageKeys(long expansionId) {
		List<StoredCardImage> storedImages = cardImageRepository.findByExpansionIdAndImageSource(expansionId, SOURCE);
		Set<ImageKey> keys = new HashSet<>();
		for (StoredCardImage storedImage : storedImages) {
			if (storedImage.hasImage()) {
				keys.add(new ImageKey(storedImage.getBlueprintId(), normalizeNumber(storedImage.getCollectorNumber())));
			}
		}
		return keys;
	}

	private void finishCurrentExpansion(CatalogExpansion expansion, int totalBlueprints, int imagesAlreadyPresent) {
		update(snapshot -> snapshot.expansionResults.add(new ImageBackfillExpansionResultResponse(
				expansion.id(),
				expansion.name(),
				expansion.code(),
				totalBlueprints,
				imagesAlreadyPresent,
				snapshot.currentExpansionSavedImages,
				snapshot.currentExpansionSkippedWithoutCollectorNumber,
				snapshot.currentExpansionNotFound,
				snapshot.currentExpansionErrors,
				imagesAlreadyPresent + snapshot.currentExpansionSavedImages)));
	}

	private boolean shouldStop() {
		return stopRequested || Thread.currentThread().isInterrupted();
	}

	private void markStopped() {
		synchronized (lock) {
			status.state = ImageBackfillRunState.STOPPED;
			status.finishedAt = Instant.now();
			status.currentCard = null;
		}
		LOGGER.info("Slow image backfill stopped by request");
	}

	private void pauseBetweenCards() throws InterruptedException {
		pause(properties.getDelay().toMillis());
	}

	private void pauseBetweenRetries() throws InterruptedException {
		pause(properties.getRetryDelay().toMillis());
	}

	private void pause(long millis) throws InterruptedException {
		if (millis <= 0) {
			return;
		}
		long remaining = millis;
		while (remaining > 0) {
			if (shouldStop()) {
				throw new InterruptedException("Image backfill stopped");
			}
			long slice = Math.min(remaining, 250L);
			Thread.sleep(slice);
			remaining -= slice;
		}
	}

	private void update(StatusUpdate update) {
		synchronized (lock) {
			update.apply(status);
		}
	}

	private void fail(String message) {
		synchronized (lock) {
			status.state = ImageBackfillRunState.FAILED;
			status.finishedAt = Instant.now();
			status.lastError = message == null || message.isBlank() ? "Errore imprevisto" : message;
			status.errors++;
		}
	}

	private void recordProcessedBlueprint(MutableStatus snapshot) {
		snapshot.processedBlueprints++;
		snapshot.currentExpansionProcessedBlueprints++;
	}

	private static Optional<String> collectorNumber(String version) {
		if (version == null || version.isBlank()) {
			return Optional.empty();
		}
		Matcher matcher = COLLECTOR_NUMBER_PATTERN.matcher(version);
		while (matcher.find()) {
			String candidate = matcher.group(1);
			if (candidate != null && !candidate.isBlank()) {
				return Optional.of(candidate.toUpperCase(Locale.ROOT));
			}
		}
		return Optional.empty();
	}

	private static String normalizeNumber(String value) {
		if (value == null) {
			return "";
		}
		String normalized = value.trim().toUpperCase(Locale.ROOT);
		Matcher matcher = Pattern.compile("^([A-Z]*)(0*)(\\d+)([A-Z]*)$").matcher(normalized);
		if (matcher.matches()) {
			return matcher.group(1) + Integer.parseInt(matcher.group(3)) + matcher.group(4);
		}
		return normalized;
	}

	private static String safeMessage(RuntimeException exception) {
		return exception.getMessage() == null || exception.getMessage().isBlank()
				? exception.getClass().getSimpleName()
				: exception.getMessage();
	}

	private record ImageKey(long blueprintId, String collectorNumber) {
	}

	private record ResolutionResult(boolean found, String errorMessage) {

		static ResolutionResult imageFound() {
			return new ResolutionResult(true, null);
		}

		static ResolutionResult notFound() {
			return new ResolutionResult(false, null);
		}

		static ResolutionResult error(String errorMessage) {
			return new ResolutionResult(false, errorMessage);
		}
	}

	@FunctionalInterface
	private interface StatusUpdate {

		void apply(MutableStatus status);
	}

	private static final class MutableStatus {

		private ImageBackfillRunState state;
		private Instant startedAt;
		private Instant finishedAt;
		private int totalExpansions;
		private int processedExpansions;
		private int totalBlueprints;
		private int processedBlueprints;
		private int savedImages;
		private int alreadyPresentImages;
		private int currentExpansionTotalBlueprints;
		private int currentExpansionProcessedBlueprints;
		private int currentExpansionImagesAlreadyPresent;
		private int currentExpansionSavedImages;
		private int currentExpansionNotFound;
		private int currentExpansionSkippedWithoutCollectorNumber;
		private int currentExpansionErrors;
		private int skippedWithoutCollectorNumber;
		private int skippedWithoutPokemonCandidate;
		private int skippedWithoutReliableMatch;
		private int errors;
		private String currentExpansion;
		private String currentCard;
		private String lastError;
		private final List<ImageBackfillExpansionResultResponse> expansionResults = new ArrayList<>();

		private static MutableStatus disabled() {
			MutableStatus status = new MutableStatus();
			status.state = ImageBackfillRunState.DISABLED;
			return status;
		}

		private static MutableStatus idle() {
			MutableStatus status = new MutableStatus();
			status.state = ImageBackfillRunState.IDLE;
			return status;
		}

		private static MutableStatus running() {
			MutableStatus status = new MutableStatus();
			status.state = ImageBackfillRunState.RUNNING;
			status.startedAt = Instant.now();
			return status;
		}

		private ImageBackfillStatusResponse toResponse(boolean enabled) {
			return new ImageBackfillStatusResponse(
					enabled,
					state,
					startedAt,
					finishedAt,
					totalExpansions,
					processedExpansions,
					totalBlueprints,
					processedBlueprints,
					savedImages,
					alreadyPresentImages,
					currentExpansionTotalBlueprints,
					currentExpansionProcessedBlueprints,
					currentExpansionImagesAlreadyPresent,
					currentExpansionSavedImages,
					currentExpansionNotFound,
					skippedWithoutCollectorNumber,
					skippedWithoutPokemonCandidate,
					skippedWithoutReliableMatch,
					errors,
					currentExpansion,
					currentCard,
					lastError,
					List.copyOf(expansionResults));
		}
	}
}
