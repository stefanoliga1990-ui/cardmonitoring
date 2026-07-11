package com.example.cardmonitoring.identification;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.cardmonitoring.catalog.CatalogBlueprint;
import com.example.cardmonitoring.catalog.CatalogExpansion;
import com.example.cardmonitoring.catalog.CatalogService;
import com.example.cardmonitoring.pokemontcg.PokemonTcgCardCandidate;
import com.example.cardmonitoring.pokemontcg.PokemonTcgClient;

@Service
public class CardIdentificationService {

	private static final int PRIMARY_SEARCH_LIMIT = 80;
	private static final int FALLBACK_SEARCH_LIMIT = 120;
	private static final Duration CACHE_TTL = Duration.ofHours(6);
	private static final Pattern COLLECTOR_NUMBER_PATTERN = Pattern.compile(
			"(?i)(?:^|\\s|\\|)([a-z]*)(\\d+)([a-z]*)\\s*(?:/\\s*([a-z]*\\d+[a-z]*))?");
	private static final Pattern SPLIT_NUMBER_PATTERN = Pattern.compile("^([^/]+)/([^/]+)$");

	private final PokemonTcgClient pokemonTcgClient;
	private final CatalogService catalogService;
	private final Map<SearchInput, CacheEntry<List<CardIdentificationCandidate>>> cache = new LinkedHashMap<>();

	public CardIdentificationService(PokemonTcgClient pokemonTcgClient, CatalogService catalogService) {
		this.pokemonTcgClient = pokemonTcgClient;
		this.catalogService = catalogService;
	}

	public synchronized List<CardIdentificationCandidate> findCandidates(CardIdentificationRequest request) {
		SearchInput input = SearchInput.from(request);
		Instant now = Instant.now();
		CacheEntry<List<CardIdentificationCandidate>> cached = cache.get(input);
		if (cached != null && cached.isFresh(now)) {
			return cached.value();
		}

		List<PokemonTcgCardCandidate> pokemonCandidates = searchPokemonTcgCandidates(input);
		List<CatalogExpansion> expansions = catalogService.getPokemonExpansions();
		List<CardIdentificationCandidate> matches = pokemonCandidates.stream()
				.filter(candidate -> numberMatches(input.number(), candidate.number()))
				.map(candidate -> mapCandidate(input, candidate, expansions))
				.sorted(Comparator
						.comparing(CardIdentificationCandidateMatch::selectable, Comparator.reverseOrder())
						.thenComparing(CardIdentificationCandidateMatch::totalMatches, Comparator.reverseOrder())
						.thenComparing(Comparator.comparingInt(CardIdentificationCandidateMatch::score).reversed())
						.thenComparing(match -> safe(match.candidate().pokemonTcgSetReleaseDate()),
								String.CASE_INSENSITIVE_ORDER)
						.thenComparing(match -> safe(match.candidate().pokemonTcgSetName()),
								String.CASE_INSENSITIVE_ORDER)
						.thenComparing(match -> safe(match.candidate().pokemonTcgCardId()),
								String.CASE_INSENSITIVE_ORDER))
				.map(CardIdentificationCandidateMatch::candidate)
				.toList();

		cache.put(input, new CacheEntry<>(matches, now.plus(CACHE_TTL)));
		return matches;
	}

	private List<PokemonTcgCardCandidate> searchPokemonTcgCandidates(SearchInput input) {
		String primaryQuery = "name:" + quoted(input.name()) + " number:" + searchNumber(input.number());
		List<PokemonTcgCardCandidate> candidates = pokemonTcgClient.searchCards(primaryQuery, PRIMARY_SEARCH_LIMIT);
		if (!candidates.isEmpty()) {
			return candidates;
		}
		String fallbackQuery = "name:" + quoted(input.name());
		return pokemonTcgClient.searchCards(fallbackQuery, FALLBACK_SEARCH_LIMIT);
	}

	private CardIdentificationCandidateMatch mapCandidate(
			SearchInput input,
			PokemonTcgCardCandidate candidate,
			List<CatalogExpansion> expansions) {
		Optional<ExpansionMatch> expansionMatch = expansions.stream()
				.map(expansion -> new ExpansionMatch(expansion, expansionScore(candidate.setName(), expansion)))
				.filter(match -> match.score() >= 55)
				.max(Comparator
						.comparingInt(ExpansionMatch::score)
						.thenComparing(match -> safe(match.expansion().name()), String.CASE_INSENSITIVE_ORDER));

		Optional<BlueprintMatch> blueprintMatch = expansionMatch.flatMap(match ->
				findBlueprintMatch(input, candidate, match.expansion(), match.score()));
		boolean selectable = blueprintMatch.isPresent();
		boolean totalMatches = totalMatches(input.total(), candidate, blueprintMatch.orElse(null));
		int score = blueprintMatch.map(BlueprintMatch::score)
				.orElseGet(() -> expansionMatch.map(ExpansionMatch::score).orElse(0));
		String confidence = selectable
				? (score >= 290 && totalMatches ? "HIGH" : "MEDIUM")
				: "LOW";

		CatalogExpansion expansion = expansionMatch.map(ExpansionMatch::expansion).orElse(null);
		CatalogBlueprint blueprint = blueprintMatch.map(BlueprintMatch::blueprint).orElse(null);
		CardIdentificationCandidate response = new CardIdentificationCandidate(
				candidate.id(),
				candidate.setId(),
				candidate.name(),
				candidate.number(),
				displayNumber(input, candidate),
				candidate.setName(),
				candidate.setSeries(),
				candidate.setPrintedTotal(),
				candidate.setTotal(),
				candidate.setReleaseDate(),
				candidate.smallImageUrl(),
				candidate.largeImageUrl(),
				blueprint == null ? null : expansion.id(),
				blueprint == null ? null : expansion.name(),
				blueprint == null ? null : expansion.code(),
				blueprint == null ? null : blueprint.id(),
				blueprint == null ? null : blueprint.name(),
				blueprint == null ? null : blueprint.version(),
				confidence,
				selectable);
		return new CardIdentificationCandidateMatch(response, score, totalMatches, selectable);
	}

	private Optional<BlueprintMatch> findBlueprintMatch(
			SearchInput input,
			PokemonTcgCardCandidate candidate,
			CatalogExpansion expansion,
			int expansionScore) {
		return catalogService.getPokemonBlueprints(expansion.id()).stream()
				.map(blueprint -> blueprintScore(input, candidate, blueprint, expansionScore))
				.flatMap(Optional::stream)
				.max(Comparator
						.comparingInt(BlueprintMatch::score)
						.thenComparing(match -> safe(match.blueprint().version()), String.CASE_INSENSITIVE_ORDER)
						.thenComparingLong(match -> match.blueprint().id()));
	}

	private Optional<BlueprintMatch> blueprintScore(
			SearchInput input,
			PokemonTcgCardCandidate candidate,
			CatalogBlueprint blueprint,
			int expansionScore) {
		if (!equalsNormalized(candidate.name(), blueprint.name())) {
			return Optional.empty();
		}
		Optional<CollectorNumber> collectorNumber = collectorNumber(blueprint.version());
		if (collectorNumber.isEmpty() || !numberMatches(candidate.number(), collectorNumber.get().number())) {
			return Optional.empty();
		}
		int score = expansionScore + 180;
		if (totalMatches(input.total(), candidate, new BlueprintMatch(blueprint, 0, collectorNumber.get()))) {
			score += 40;
		}
		return Optional.of(new BlueprintMatch(blueprint, score, collectorNumber.get()));
	}

	private static int expansionScore(String pokemonTcgSetName, CatalogExpansion expansion) {
		String candidate = normalizeText(pokemonTcgSetName);
		String cardTrader = normalizeText(expansion.name());
		if (candidate.isBlank() || cardTrader.isBlank()) {
			return 0;
		}
		if (candidate.equals(cardTrader)) {
			return 110;
		}
		if ((candidate + " set").equals(cardTrader) || candidate.equals(cardTrader + " set")) {
			return 100;
		}
		if (cardTrader.contains(candidate) || candidate.contains(cardTrader)) {
			return 55;
		}
		return 0;
	}

	private static Optional<CollectorNumber> collectorNumber(String version) {
		if (!StringUtils.hasText(version)) {
			return Optional.empty();
		}
		Matcher matcher = COLLECTOR_NUMBER_PATTERN.matcher(version);
		while (matcher.find()) {
			String prefix = safe(matcher.group(1)).toUpperCase(Locale.ROOT);
			String number = prefix + matcher.group(2) + safe(matcher.group(3)).toUpperCase(Locale.ROOT);
			String total = matcher.group(4);
			return Optional.of(new CollectorNumber(number, total == null ? "" : total.toUpperCase(Locale.ROOT)));
		}
		return Optional.empty();
	}

	private static boolean totalMatches(
			String requestedTotal,
			PokemonTcgCardCandidate candidate,
			BlueprintMatch blueprintMatch) {
		if (!StringUtils.hasText(requestedTotal)) {
			return false;
		}
		String normalizedTotal = normalizeNumber(requestedTotal);
		if (candidate.setPrintedTotal() != null
				&& normalizedTotal.equals(String.valueOf(candidate.setPrintedTotal()))) {
			return true;
		}
		if (candidate.setTotal() != null && normalizedTotal.equals(String.valueOf(candidate.setTotal()))) {
			return true;
		}
		return blueprintMatch != null
				&& normalizedTotal.equals(normalizeNumber(blueprintMatch.collectorNumber().total()));
	}

	private static String displayNumber(SearchInput input, PokemonTcgCardCandidate candidate) {
		if (StringUtils.hasText(input.total())) {
			return candidate.number() + "/" + input.total();
		}
		if (hasLetters(candidate.number())) {
			return candidate.number();
		}
		if (candidate.setPrintedTotal() != null) {
			return candidate.number() + "/" + candidate.setPrintedTotal();
		}
		if (candidate.setTotal() != null) {
			return candidate.number() + "/" + candidate.setTotal();
		}
		return candidate.number();
	}

	private static boolean numberMatches(String expected, String actual) {
		return normalizeNumber(expected).equals(normalizeNumber(actual));
	}

	private static String searchNumber(String value) {
		return hasLetters(value) ? value.toUpperCase(Locale.ROOT) : normalizeNumber(value);
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

	private static boolean hasLetters(String value) {
		return value != null && value.matches(".*[A-Za-z].*");
	}

	private static boolean equalsNormalized(String left, String right) {
		return normalizeText(left).equals(normalizeText(right));
	}

	private static String normalizeText(String value) {
		return value == null
				? ""
				: value.trim().toLowerCase(Locale.ROOT)
						.replaceAll("[^a-z0-9]+", " ")
						.replaceAll("\\s+", " ")
						.trim();
	}

	private static String quoted(String value) {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}

	private record SearchInput(String name, String number, String total) {

		private static SearchInput from(CardIdentificationRequest request) {
			if (request == null) {
				throw new IllegalArgumentException("request is required");
			}
			String name = normalizeRequired(request.name(), "name");
			String number = normalizeRequired(request.number(), "number");
			String total = request.total() == null ? "" : request.total().trim().toUpperCase(Locale.ROOT);
			Matcher splitNumber = SPLIT_NUMBER_PATTERN.matcher(number);
			if (splitNumber.matches()) {
				number = splitNumber.group(1).trim().toUpperCase(Locale.ROOT);
				if (!StringUtils.hasText(total)) {
					total = splitNumber.group(2).trim().toUpperCase(Locale.ROOT);
				}
			}
			if (!number.matches("[A-Z0-9-]+")) {
				throw new IllegalArgumentException("number must contain only letters, digits or hyphens");
			}
			if (StringUtils.hasText(total) && !total.matches("[A-Z0-9-]+")) {
				throw new IllegalArgumentException("total must contain only letters, digits or hyphens");
			}
			return new SearchInput(name, number, total);
		}

		private static String normalizeRequired(String value, String fieldName) {
			if (!StringUtils.hasText(value)) {
				throw new IllegalArgumentException(fieldName + " is required");
			}
			return value.trim().toUpperCase(Locale.ROOT);
		}
	}

	private record ExpansionMatch(CatalogExpansion expansion, int score) {
	}

	private record BlueprintMatch(CatalogBlueprint blueprint, int score, CollectorNumber collectorNumber) {
	}

	private record CollectorNumber(String number, String total) {
	}

	private record CardIdentificationCandidateMatch(
			CardIdentificationCandidate candidate,
			int score,
			boolean totalMatches,
			boolean selectable) {
	}

	private record CacheEntry<T>(T value, Instant expiresAt) {

		boolean isFresh(Instant now) {
			return now.isBefore(expiresAt);
		}
	}
}
