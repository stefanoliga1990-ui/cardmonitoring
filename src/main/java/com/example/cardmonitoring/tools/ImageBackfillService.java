package com.example.cardmonitoring.tools;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.cardmonitoring.catalog.CatalogBlueprint;
import com.example.cardmonitoring.catalog.CatalogCard;
import com.example.cardmonitoring.catalog.CatalogExpansion;
import com.example.cardmonitoring.pokemontcg.CardImage;
import com.example.cardmonitoring.pokemontcg.PokemonTcgCardCandidate;
import com.example.cardmonitoring.pokemontcg.PokemonTcgCardPage;
import com.example.cardmonitoring.pokemontcg.PokemonTcgClient;
import com.example.cardmonitoring.catalog.CatalogService;
import com.example.cardmonitoring.tools.ImageBackfillPersistenceService.SaveResult;

@Service
public class ImageBackfillService {

	private static final Logger LOGGER = LoggerFactory.getLogger(ImageBackfillService.class);
	private static final String SOURCE = "POKEMON_TCG_API";
	private static final int MINIMUM_RELIABLE_SET_SCORE = 40;
	private static final Pattern COLLECTOR_NUMBER_PATTERN = Pattern.compile(
			"(?i)(?:^|\\s|\\|)([a-z]*\\d+[a-z]*)\\s*(?:/\\s*([a-z]*\\d+[a-z]*))?");

	private final ImageBackfillProperties properties;
	private final ExecutorService executorService;
	private final PokemonTcgClient pokemonTcgClient;
	private final CatalogService catalogService;
	private final ImageBackfillPersistenceService persistenceService;
	private final Object lock = new Object();

	private MutableStatus status = MutableStatus.disabled();

	public ImageBackfillService(
			ImageBackfillProperties properties,
			ExecutorService imageBackfillExecutor,
			PokemonTcgClient pokemonTcgClient,
			CatalogService catalogService,
			ImageBackfillPersistenceService persistenceService) {
		this.properties = properties;
		this.executorService = imageBackfillExecutor;
		this.pokemonTcgClient = pokemonTcgClient;
		this.catalogService = catalogService;
		this.persistenceService = persistenceService;
	}

	public ImageBackfillStatusResponse start() {
		if (!properties.isEnabled()) {
			return disabledStatus();
		}
		synchronized (lock) {
			if (status.state == ImageBackfillRunState.RUNNING) {
				return status.toResponse(true);
			}
			status = MutableStatus.running();
			executorService.submit(this::run);
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
			status = MutableStatus.disabled();
			return status.toResponse(false);
		}
	}

	private void run() {
		try {
			LOGGER.info("Image backfill started");
			Map<CardKey, List<PokemonTcgCardCandidate>> pokemonCards = fetchPokemonCards();
			List<CatalogExpansion> expansions = catalogService.getPokemonExpansions();
			update(snapshot -> snapshot.totalExpansions = expansions.size());

			for (CatalogExpansion expansion : expansions) {
				processExpansion(expansion, pokemonCards);
				pauseIfConfigured();
			}

			synchronized (lock) {
				status.state = ImageBackfillRunState.COMPLETED;
				status.finishedAt = Instant.now();
			}
			LOGGER.info("Image backfill completed: {}", status());
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			fail("Image backfill interrupted");
		}
		catch (RuntimeException exception) {
			LOGGER.error("Image backfill failed", exception);
			fail(exception.getMessage());
		}
	}

	private Map<CardKey, List<PokemonTcgCardCandidate>> fetchPokemonCards() throws InterruptedException {
		Map<CardKey, List<PokemonTcgCardCandidate>> index = new HashMap<>();
		int page = 1;
		int fetched = 0;
		int totalCount = Integer.MAX_VALUE;
		while (fetched < totalCount) {
			PokemonTcgCardPage cardPage = pokemonTcgClient.getCardsPage(page, properties.getPokemonPageSize());
			if (cardPage.cards().isEmpty()) {
				LOGGER.info("Pokemon TCG image backfill page returned no cards, stopping: page={}", page);
				break;
			}
			for (PokemonTcgCardCandidate card : cardPage.cards()) {
				index.computeIfAbsent(CardKey.from(card.name(), card.number()), ignored -> new ArrayList<>()).add(card);
			}
			fetched += cardPage.cards().size();
			totalCount = cardPage.totalCount() > 0 ? cardPage.totalCount() : fetched;
			int currentPage = page;
			int currentFetched = fetched;
			int currentTotal = totalCount;
			update(snapshot -> {
				snapshot.pokemonPagesFetched = currentPage;
				snapshot.totalPokemonCards = currentFetched;
				snapshot.currentExpansion = "Caricamento immagini Pokémon TCG";
				snapshot.currentCard = currentFetched + " / " + currentTotal;
			});
			LOGGER.info("Pokemon TCG image backfill page indexed: page={}, pageSize={}, fetched={}, totalCount={}",
					page, cardPage.pageSize(), fetched, totalCount);
			page++;
			pauseIfConfigured();
		}
		return Map.copyOf(index);
	}

	private void processExpansion(
			CatalogExpansion expansion,
			Map<CardKey, List<PokemonTcgCardCandidate>> pokemonCards) throws InterruptedException {
		LOGGER.info("Image backfill processing CardTrader expansion: expansionId={}, name='{}'",
				expansion.id(), expansion.name());
		update(snapshot -> {
			snapshot.currentExpansion = expansion.name();
			snapshot.currentCard = null;
		});

		List<CatalogBlueprint> blueprints;
		try {
			blueprints = catalogService.getPokemonBlueprints(expansion.id());
		}
		catch (RuntimeException exception) {
			LOGGER.warn("Image backfill expansion skipped: expansionId={}, name='{}', error={}",
					expansion.id(), expansion.name(), exception.getMessage());
			update(snapshot -> {
				snapshot.errors++;
				snapshot.lastError = "Set " + expansion.name() + ": " + safeMessage(exception);
				snapshot.processedExpansions++;
			});
			return;
		}
		update(snapshot -> snapshot.totalBlueprints += blueprints.size());

		for (CatalogBlueprint blueprint : blueprints) {
			processBlueprint(expansion, blueprint, pokemonCards);
		}
		update(snapshot -> snapshot.processedExpansions++);
	}

	private void processBlueprint(
			CatalogExpansion expansion,
			CatalogBlueprint blueprint,
			Map<CardKey, List<PokemonTcgCardCandidate>> pokemonCards) {
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
				snapshot.processedBlueprints++;
			});
			return;
		}

		List<PokemonTcgCardCandidate> candidates = pokemonCards.getOrDefault(
				CardKey.from(blueprint.name(), collectorNumber.get()), List.of());
		if (candidates.isEmpty()) {
			update(snapshot -> {
				snapshot.skippedWithoutPokemonCandidate++;
				snapshot.processedBlueprints++;
			});
			return;
		}

		Optional<PokemonTcgCardCandidate> selected = selectCandidate(card, candidates);
		if (selected.isEmpty()) {
			LOGGER.debug(
					"Image backfill skipped without reliable set match: blueprintId={}, cardName='{}', collectorNumber={}, expansion='{}', candidateCount={}",
					blueprint.id(), blueprint.name(), collectorNumber.get(), expansion.name(), candidates.size());
			update(snapshot -> {
				snapshot.skippedWithoutReliableMatch++;
				snapshot.processedBlueprints++;
			});
			return;
		}

		try {
			SaveResult result = persistenceService.saveOrRefresh(
					card,
					collectorNumber.get(),
					toCardImage(selected.get()),
					Instant.now());
			update(snapshot -> {
				if (result == SaveResult.SAVED) {
					snapshot.savedImages++;
				}
				else if (result == SaveResult.UPDATED) {
					snapshot.updatedImages++;
				}
				else {
					snapshot.alreadyPresentImages++;
				}
				snapshot.processedBlueprints++;
			});
		}
		catch (RuntimeException exception) {
			LOGGER.warn("Image backfill card save failed: blueprintId={}, cardName='{}', error={}",
					blueprint.id(), blueprint.name(), exception.getMessage());
			update(snapshot -> {
				snapshot.errors++;
				snapshot.lastError = blueprint.name() + ": " + safeMessage(exception);
				snapshot.processedBlueprints++;
			});
		}
	}

	private static Optional<PokemonTcgCardCandidate> selectCandidate(
			CatalogCard card,
			List<PokemonTcgCardCandidate> candidates) {
		return candidates.stream()
				.map(candidate -> new ScoredCandidate(candidate, setMatchScore(card.expansionName(), candidate.setName())))
				.filter(candidate -> candidate.score() >= MINIMUM_RELIABLE_SET_SCORE)
				.max(Comparator
						.comparingInt(ScoredCandidate::score)
						.thenComparing(candidate -> candidate.candidate().id()))
				.map(ScoredCandidate::candidate);
	}

	private static int setMatchScore(String cardTraderSetName, String pokemonTcgSetName) {
		if (equalsNormalized(cardTraderSetName, pokemonTcgSetName)) {
			return 100;
		}
		if (equalsNormalized(removeSetWords(cardTraderSetName), removeSetWords(pokemonTcgSetName))) {
			return 80;
		}
		if (containsNormalized(cardTraderSetName, pokemonTcgSetName)
				|| containsNormalized(pokemonTcgSetName, cardTraderSetName)
				|| containsNormalized(removeSetWords(cardTraderSetName), removeSetWords(pokemonTcgSetName))
				|| containsNormalized(removeSetWords(pokemonTcgSetName), removeSetWords(cardTraderSetName))) {
			return 40;
		}
		return 0;
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

	private void pauseIfConfigured() throws InterruptedException {
		long millis = properties.getDelay().toMillis();
		if (millis > 0) {
			Thread.sleep(millis);
		}
	}

	private static CardImage toCardImage(PokemonTcgCardCandidate candidate) {
		return new CardImage(candidate.smallImageUrl(), candidate.largeImageUrl(), SOURCE, candidate.id());
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

	private static boolean equalsNormalized(String left, String right) {
		return normalizeText(left).equals(normalizeText(right));
	}

	private static boolean containsNormalized(String container, String value) {
		String normalizedContainer = normalizeText(container);
		String normalizedValue = normalizeText(value);
		return !normalizedContainer.isBlank()
				&& !normalizedValue.isBlank()
				&& normalizedContainer.contains(normalizedValue);
	}

	private static String removeSetWords(String value) {
		return normalizeText(value)
				.replaceAll("\\b(set|expansion|pack|collection)\\b", " ")
				.replaceAll("\\s+", " ")
				.trim();
	}

	private static String normalizeText(String value) {
		if (value == null) {
			return "";
		}
		String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
				.replaceAll("\\p{M}+", "");
		return withoutAccents.trim().toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]+", " ")
				.replaceAll("\\s+", " ")
				.trim();
	}

	private static String safeMessage(RuntimeException exception) {
		return exception.getMessage() == null || exception.getMessage().isBlank()
				? exception.getClass().getSimpleName()
				: exception.getMessage();
	}

	private record CardKey(String name, String number) {

		static CardKey from(String name, String number) {
			return new CardKey(normalizeText(name), normalizeNumber(number));
		}
	}

	private record ScoredCandidate(PokemonTcgCardCandidate candidate, int score) {
	}

	@FunctionalInterface
	private interface StatusUpdate {

		void apply(MutableStatus status);
	}

	private static final class MutableStatus {

		private ImageBackfillRunState state;
		private Instant startedAt;
		private Instant finishedAt;
		private int pokemonPagesFetched;
		private int totalPokemonCards;
		private int totalExpansions;
		private int processedExpansions;
		private int totalBlueprints;
		private int processedBlueprints;
		private int savedImages;
		private int updatedImages;
		private int alreadyPresentImages;
		private int skippedWithoutCollectorNumber;
		private int skippedWithoutPokemonCandidate;
		private int skippedWithoutReliableMatch;
		private int errors;
		private String currentExpansion;
		private String currentCard;
		private String lastError;

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
					pokemonPagesFetched,
					totalPokemonCards,
					totalExpansions,
					processedExpansions,
					totalBlueprints,
					processedBlueprints,
					savedImages,
					updatedImages,
					alreadyPresentImages,
					skippedWithoutCollectorNumber,
					skippedWithoutPokemonCandidate,
					skippedWithoutReliableMatch,
					errors,
					currentExpansion,
					currentCard,
					lastError);
		}
	}
}
