package com.example.cardmonitoring.catalog;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.example.cardmonitoring.cardtrader.CardTraderBlueprint;
import com.example.cardmonitoring.cardtrader.CardTraderCategory;
import com.example.cardmonitoring.cardtrader.CardTraderClient;
import com.example.cardmonitoring.cardtrader.CardTraderException;
import com.example.cardmonitoring.cardtrader.CardTraderGame;

@Service
public class CatalogService {

	static final long POKEMON_GAME_ID = 5L;
	static final long POKEMON_SINGLES_CATEGORY_ID = 73L;
	private static final Duration CACHE_TTL = Duration.ofHours(6);
	private static final Pattern UNSUPPORTED_ALPHANUMERIC_COLLECTOR = Pattern.compile("(?i)\\b[a-z]+\\d+\\b");
	private static final Pattern YEAR_AND_STAMP_VERSION = Pattern.compile("^\\s*\\d{4}\\s*\\|.+$");

	private static final Comparator<CatalogExpansion> EXPANSION_ORDER = Comparator
			.comparing(CatalogExpansion::name, String.CASE_INSENSITIVE_ORDER)
			.thenComparingLong(CatalogExpansion::id);
	private static final Comparator<CatalogBlueprint> BLUEPRINT_ORDER = Comparator
			.comparing(CatalogService::collectorNumberSortKey)
			.thenComparing(CatalogBlueprint::name, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(blueprint -> blueprint.version() == null ? "" : blueprint.version(),
					String.CASE_INSENSITIVE_ORDER)
			.thenComparingLong(CatalogBlueprint::id);

	private final CardTraderClient cardTraderClient;
	private final Map<Long, CacheEntry<List<CatalogBlueprint>>> blueprintCache = new HashMap<>();
	private CacheEntry<List<CatalogExpansion>> expansionCache;

	public CatalogService(CardTraderClient cardTraderClient) {
		this.cardTraderClient = cardTraderClient;
	}

	public synchronized List<CatalogExpansion> getPokemonExpansions() {
		Instant now = Instant.now();
		if (expansionCache != null && expansionCache.isFresh(now)) {
			return expansionCache.value();
		}

		validatePokemonCatalog();
		List<CatalogExpansion> expansions = cardTraderClient.getExpansions().stream()
				.filter(expansion -> expansion.gameId() == POKEMON_GAME_ID)
				.filter(OfficialPokemonSetCatalog::includes)
				.map(expansion -> new CatalogExpansion(expansion.id(), expansion.name(), expansion.code()))
				.sorted(EXPANSION_ORDER)
				.toList();
		expansionCache = new CacheEntry<>(expansions, now.plus(CACHE_TTL));
		return expansions;
	}

	public synchronized List<CatalogBlueprint> getPokemonBlueprints(long expansionId) {
		if (expansionId <= 0 || getPokemonExpansions().stream().noneMatch(expansion -> expansion.id() == expansionId)) {
			throw new CatalogNotFoundException("Pokemon expansion not found: " + expansionId);
		}

		Instant now = Instant.now();
		CacheEntry<List<CatalogBlueprint>> cachedBlueprints = blueprintCache.get(expansionId);
		if (cachedBlueprints != null && cachedBlueprints.isFresh(now)) {
			return cachedBlueprints.value();
		}

		List<CatalogBlueprint> blueprints = cardTraderClient.getBlueprints(expansionId).stream()
				.filter(blueprint -> isPokemonSinglesBlueprint(blueprint, expansionId))
				.filter(blueprint -> hasSupportedCollectorIdentity(blueprint.version()))
				.map(blueprint -> new CatalogBlueprint(
						blueprint.id(),
						CardNameNormalizer.withoutTrailingNumericLevel(blueprint.name()),
						blueprint.version(),
						blueprint.expansionId()))
				.sorted(BLUEPRINT_ORDER)
				.toList();
		blueprintCache.put(expansionId, new CacheEntry<>(blueprints, now.plus(CACHE_TTL)));
		return blueprints;
	}

	public CatalogCard resolvePokemonCard(long expansionId, long blueprintId) {
		CatalogExpansion expansion = getPokemonExpansions().stream()
				.filter(candidate -> candidate.id() == expansionId)
				.findFirst()
				.orElseThrow(() -> new CatalogNotFoundException(
						"Pokemon expansion not found: " + expansionId));
		CatalogBlueprint blueprint = getPokemonBlueprints(expansionId).stream()
				.filter(candidate -> candidate.id() == blueprintId)
				.findFirst()
				.orElseThrow(() -> new CatalogNotFoundException(
						"Pokemon blueprint not found: " + blueprintId));
		if (isBlank(expansion.name()) || isBlank(expansion.code())
				|| isBlank(blueprint.name()) || isBlank(blueprint.version())) {
			throw CardTraderException.invalidResponse(null);
		}
		return new CatalogCard(
				blueprint.id(),
				blueprint.name(),
				blueprint.version(),
				expansion.id(),
				expansion.name(),
				expansion.code());
	}

	private void validatePokemonCatalog() {
		List<CardTraderGame> games = cardTraderClient.getGames();
		boolean pokemonGameExists = games.stream().anyMatch(game -> game.id() == POKEMON_GAME_ID);
		List<CardTraderCategory> categories = cardTraderClient.getCategories(POKEMON_GAME_ID);
		boolean singlesCategoryExists = categories.stream().anyMatch(category ->
				category.id() == POKEMON_SINGLES_CATEGORY_ID && category.gameId() == POKEMON_GAME_ID);
		if (!pokemonGameExists || !singlesCategoryExists) {
			throw CardTraderException.invalidResponse(null);
		}
	}

	private static boolean isPokemonSinglesBlueprint(CardTraderBlueprint blueprint, long expansionId) {
		return blueprint.expansionId() == expansionId
				&& blueprint.categoryId() == POKEMON_SINGLES_CATEGORY_ID;
	}

	private static boolean hasSupportedCollectorIdentity(String version) {
		if (version == null || version.isBlank()) return false;
		if (CollectorNumberParser.fromVersion(version).isPresent()) return true;
		// Some deck products use a release year and a stamp description rather than a card number.
		// Keep them available for the safe name-only image fallback, while retaining the exclusion of
		// true alphanumeric collector numbers such as MEW001.
		return YEAR_AND_STAMP_VERSION.matcher(version).matches()
				&& !UNSUPPORTED_ALPHANUMERIC_COLLECTOR.matcher(version).find();
	}

	private static CollectorNumberSortKey collectorNumberSortKey(CatalogBlueprint blueprint) {
		String version = blueprint.version();
		if (version == null || version.isBlank()) {
			return CollectorNumberSortKey.noNumber();
		}
		return CollectorNumberParser.fromVersion(version)
				.map(number -> {
					Matcher matcher = Pattern.compile("(?i)^(\\d+)([a-z]*)$").matcher(number);
					if (!matcher.matches()) return CollectorNumberSortKey.noNumber();
					return new CollectorNumberSortKey(false, "", Integer.parseInt(matcher.group(1)),
							matcher.group(2).toUpperCase(), "");
				})
				.orElseGet(CollectorNumberSortKey::noNumber);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private record CollectorNumberSortKey(
			boolean missing,
			String prefix,
			int number,
			String suffix,
			String total) implements Comparable<CollectorNumberSortKey> {

		private static CollectorNumberSortKey noNumber() {
			return new CollectorNumberSortKey(true, "", Integer.MAX_VALUE, "", "");
		}

		@Override
		public int compareTo(CollectorNumberSortKey other) {
			return Comparator
					.comparing(CollectorNumberSortKey::missing)
					.thenComparingInt(CollectorNumberSortKey::number)
					.thenComparing(CollectorNumberSortKey::prefix, String.CASE_INSENSITIVE_ORDER)
					.thenComparing(CollectorNumberSortKey::suffix, String.CASE_INSENSITIVE_ORDER)
					.thenComparing(CollectorNumberSortKey::total, String.CASE_INSENSITIVE_ORDER)
					.compare(this, other);
		}
	}

	private record CacheEntry<T>(T value, Instant expiresAt) {

		boolean isFresh(Instant now) {
			return now.isBefore(expiresAt);
		}
	}
}
