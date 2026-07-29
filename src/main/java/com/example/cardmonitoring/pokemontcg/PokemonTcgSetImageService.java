package com.example.cardmonitoring.pokemontcg;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.example.cardmonitoring.catalog.CatalogCard;

/** Resolves cards by the much more reliable Pokemon TCG API set id plus collector number. */
@Service
public class PokemonTcgSetImageService {
	private static final Pattern NUMBER = Pattern.compile("^([A-Z]*)(0*)(\\d+)([A-Z]*)$");
	private final PokemonTcgClient client;
	private final PokemonTcgSetMappingRepository mappings;
	private final ConcurrentMap<String, List<PokemonTcgCardCandidate>> cardsBySetId = new ConcurrentHashMap<>();
	private volatile List<PokemonTcgSetCandidate> sets;

	public PokemonTcgSetImageService(PokemonTcgClient client, PokemonTcgSetMappingRepository mappings) {
		this.client = client;
		this.mappings = mappings;
	}

	public List<PokemonTcgCardCandidate> findCandidates(CatalogCard card, String collectorNumber) {
		return resolveSetId(card).map(this::cardsForSet).orElseGet(List::of).stream()
				.filter(candidate -> normalizeNumber(collectorNumber).equals(normalizeNumber(candidate.number())))
				.toList();
	}

	private List<PokemonTcgCardCandidate> cardsForSet(String setId) {
		return cardsBySetId.computeIfAbsent(setId, client::getCardsForSet);
	}

	private Optional<String> resolveSetId(CatalogCard card) {
		Optional<PokemonTcgSetMapping> stored = mappings.findById(card.expansionId());
		if (stored.isPresent()) return Optional.of(stored.get().getPokemonTcgSetId());
		List<PokemonTcgSetCandidate> available = availableSets();
		PokemonTcgSetCandidate winner = available.stream()
				.map(set -> new ScoredSet(set, score(card, set)))
				.filter(scored -> scored.score >= 300)
				.max(Comparator.comparingInt(ScoredSet::score))
				.map(ScoredSet::set).orElse(null);
		if (winner == null) return Optional.empty();
		mappings.save(new PokemonTcgSetMapping(card.expansionId(), winner.id(), Instant.now()));
		return Optional.of(winner.id());
	}

	private List<PokemonTcgSetCandidate> availableSets() {
		List<PokemonTcgSetCandidate> current = sets;
		if (current != null) return current;
		synchronized (this) {
			if (sets == null) sets = client.getSets();
			return sets;
		}
	}

	private static int score(CatalogCard card, PokemonTcgSetCandidate set) {
		String code = normalize(card.expansionCode());
		if (!code.isBlank() && (code.equals(normalize(set.id())) || code.equals(normalize(set.ptcgoCode())))) return 500;
		return normalize(card.expansionName()).equals(normalize(set.name())) ? 400 : 0;
	}

	private static String normalizeNumber(String value) {
		String text = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
		Matcher matcher = NUMBER.matcher(text);
		return matcher.matches() ? matcher.group(1) + Integer.parseInt(matcher.group(3)) + matcher.group(4) : text;
	}
	private static String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
	}
	private record ScoredSet(PokemonTcgSetCandidate set, int score) { }
}
