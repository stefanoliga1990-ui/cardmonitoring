package com.example.cardmonitoring.pokemontcg;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cardmonitoring.catalog.CatalogCard;
import com.example.cardmonitoring.catalog.CollectorNumberParser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Imports the supplied TCG Collector card list and uses it only as a verified
 * identity bridge between CardTrader and Pokemon TCG API. It never supplies an
 * image URL by itself.
 */
@Service
public class TcgCollectorReferenceCatalogService implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(TcgCollectorReferenceCatalogService.class);
	private static final String CATALOG_NAME = "TCG_COLLECTOR";
	private static final String RESOURCE_PATH = "catalog/tcg-collector-card-catalog.json";
	private static final Pattern CARD_NUMBER = Pattern.compile("(?i)^([a-z]*)(0*)(\\d+)([a-z]*)$");

	private final ObjectMapper objectMapper;
	private final TcgCollectorReferenceCatalogStateRepository stateRepository;
	private final TcgCollectorReferenceSetRepository setRepository;
	private final TcgCollectorReferenceCardRepository cardRepository;
	private final ConcurrentMap<Long, List<TcgCollectorReferenceCard>> cardsBySetId = new ConcurrentHashMap<>();

	public TcgCollectorReferenceCatalogService(
			ObjectMapper objectMapper,
			TcgCollectorReferenceCatalogStateRepository stateRepository,
			TcgCollectorReferenceSetRepository setRepository,
			TcgCollectorReferenceCardRepository cardRepository) {
		this.objectMapper = objectMapper;
		this.stateRepository = stateRepository;
		this.setRepository = setRepository;
		this.cardRepository = cardRepository;
	}

	@Override
	public void run(ApplicationArguments args) {
		importCatalogIfNeeded();
	}

	/**
	 * Finds a single, safe reference identity. A CardTrader collector number is
	 * accepted only together with a compatible name: CardTrader occasionally
	 * assigns a neighbouring card number to a translated product variant.
	 */
	public Optional<ReferenceCardMatch> findMatch(CatalogCard card) {
		if (card == null) return Optional.empty();
		Optional<TcgCollectorReferenceSet> referenceSet = setRepository
				.findByNormalizedName(normalizeSetName(card.expansionName()));
		if (referenceSet.isEmpty()) {
			LOGGER.debug("TCG Collector reference set not found: expansionId={}, name='{}'",
					card.expansionId(), card.expansionName());
			return Optional.empty();
		}

		List<TcgCollectorReferenceCard> referenceCards = cardsFor(referenceSet.get());
		String cardTraderNumber = CollectorNumberParser.fromVersion(card.cardVersion())
				.map(TcgCollectorReferenceCatalogService::normalizeCollectorNumber)
				.orElse("");
		if (!cardTraderNumber.isBlank()) {
			List<TcgCollectorReferenceCard> exactNumberAndName = referenceCards.stream()
					.filter(candidate -> cardTraderNumber.equals(candidate.getNormalizedCollectorNumber()))
					.filter(candidate -> namesMatch(card.cardName(), candidate.getSourceName()))
					.toList();
			if (exactNumberAndName.size() == 1) {
				return Optional.of(toMatch(referenceSet.get(), exactNumberAndName.get(0), MatchConfidence.NUMBER_AND_NAME));
			}
		}

		List<TcgCollectorReferenceCard> nameMatches = referenceCards.stream()
				.filter(candidate -> namesMatch(card.cardName(), candidate.getSourceName()))
				.toList();
		if (nameMatches.size() == 1) {
			return Optional.of(toMatch(referenceSet.get(), nameMatches.get(0), MatchConfidence.UNIQUE_NAME));
		}
		return Optional.empty();
	}

	@Transactional
	public synchronized void importCatalogIfNeeded() {
		CatalogFile catalogFile = readCatalogFile();
		Optional<TcgCollectorReferenceCatalogState> currentState = stateRepository.findById(CATALOG_NAME);
		if (currentState.isPresent() && catalogFile.digest().equals(currentState.get().getSourceDigest())) {
			return;
		}

		List<ImportedSet> importedSets = toImportedSets(catalogFile.sets());
		cardRepository.deleteAllInBatch();
		setRepository.deleteAllInBatch();
		setRepository.flush();
		cardsBySetId.clear();

		List<TcgCollectorReferenceSet> savedSets = setRepository.saveAll(importedSets.stream()
				.map(set -> new TcgCollectorReferenceSet(set.name(), set.normalizedName()))
				.toList());
		setRepository.flush();

		List<TcgCollectorReferenceCard> cards = new ArrayList<>();
		for (int index = 0; index < savedSets.size(); index++) {
			TcgCollectorReferenceSet savedSet = savedSets.get(index);
			for (ImportedCard importedCard : importedSets.get(index).cards()) {
				cards.add(new TcgCollectorReferenceCard(
						savedSet,
						importedCard.name(),
						canonicalCardName(importedCard.name()),
						importedCard.number(),
						importedCard.normalizedCollectorNumber()));
			}
		}
		cardRepository.saveAll(cards);
		stateRepository.save(new TcgCollectorReferenceCatalogState(CATALOG_NAME, catalogFile.digest(), Instant.now()));
		LOGGER.info("Imported TCG Collector reference catalog: sets={}, cards={}", savedSets.size(), cards.size());
	}

	private List<TcgCollectorReferenceCard> cardsFor(TcgCollectorReferenceSet referenceSet) {
		return cardsBySetId.computeIfAbsent(referenceSet.getId(), cardRepository::findByReferenceSetId);
	}

	private CatalogFile readCatalogFile() {
		ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
		try (InputStream input = resource.getInputStream()) {
			byte[] content = input.readAllBytes();
			List<SourceSet> sets = parseSourceSets(objectMapper.readTree(new String(content, StandardCharsets.UTF_8)));
			if (sets == null || sets.isEmpty()) throw new IllegalStateException("TCG Collector reference catalog is empty");
			return new CatalogFile(List.copyOf(sets), sha256(content));
		}
		catch (IOException exception) {
			throw new IllegalStateException("Unable to read TCG Collector reference catalog", exception);
		}
	}

	private static List<SourceSet> parseSourceSets(JsonNode root) {
		if (root == null || !root.isArray()) {
			throw new IllegalStateException("TCG Collector reference catalog must be a JSON array");
		}
		List<SourceSet> sets = new ArrayList<>();
		for (JsonNode setNode : root) {
			JsonNode cardsNode = setNode.get("carte");
			List<SourceCard> cards = new ArrayList<>();
			if (cardsNode != null && cardsNode.isArray()) {
				for (JsonNode cardNode : cardsNode) {
					cards.add(new SourceCard(text(cardNode, "nome"), text(cardNode, "numero")));
				}
			}
			sets.add(new SourceSet(text(setNode, "set"), List.copyOf(cards)));
		}
		return List.copyOf(sets);
	}

	private static String text(JsonNode node, String fieldName) {
		if (node == null || !node.isObject()) return null;
		JsonNode value = node.get(fieldName);
		return value != null && value.isString() ? value.asString() : null;
	}

	private static List<ImportedSet> toImportedSets(List<SourceSet> sourceSets) {
		List<ImportedSet> importedSets = new ArrayList<>();
		Set<String> setNames = new LinkedHashSet<>();
		for (SourceSet sourceSet : sourceSets) {
			String setName = required(sourceSet.set(), "set name");
			String normalizedSetName = normalizeSetName(setName);
			if (!setNames.add(normalizedSetName)) {
				throw new IllegalStateException("Duplicate TCG Collector reference set: " + setName);
			}
			List<ImportedCard> cards = new ArrayList<>();
			Set<String> identities = new LinkedHashSet<>();
			for (SourceCard sourceCard : sourceSet.carte() == null ? List.<SourceCard>of() : sourceSet.carte()) {
				String cardName = required(sourceCard.nome(), "card name");
				String cardNumber = required(sourceCard.numero(), "card number");
				// Deck products in the supplied source sometimes expose their origin set
				// (for example "Hidden Fates") instead of a collector number. Keep their
				// name as a safe fallback, but never pretend that value is a number.
				String normalizedNumber = normalizeCollectorNumber(cardNumber);
				String identity = canonicalCardName(cardName) + "|" + normalizedNumber;
				if (identities.add(identity)) cards.add(new ImportedCard(cardName, cardNumber, normalizedNumber));
			}
			if (cards.isEmpty()) throw new IllegalStateException("TCG Collector reference set has no cards: " + setName);
			importedSets.add(new ImportedSet(setName, normalizedSetName, List.copyOf(cards)));
		}
		return List.copyOf(importedSets);
	}

	private static ReferenceCardMatch toMatch(
			TcgCollectorReferenceSet referenceSet,
			TcgCollectorReferenceCard referenceCard,
			MatchConfidence confidence) {
		return new ReferenceCardMatch(referenceSet.getSourceName(), referenceCard.getSourceName(),
				referenceCard.getNormalizedCollectorNumber(), confidence);
	}

	static String normalizeSetName(String value) {
		return normalizeText(value).replace("amp ", "");
	}

	static String normalizeCollectorNumber(String value) {
		if (value == null) return "";
		String mainNumber = value.trim().toUpperCase(Locale.ROOT).split("/", 2)[0].trim();
		Matcher matcher = CARD_NUMBER.matcher(mainNumber);
		if (!matcher.matches()) return "";
		return matcher.group(1).toUpperCase(Locale.ROOT) + Integer.parseInt(matcher.group(3))
				+ matcher.group(4).toUpperCase(Locale.ROOT);
	}

	private static boolean namesMatch(String left, String right) {
		if (PokemonTcgSetImageService.namesCompatible(left, right)) return true;
		return singularize(canonicalCardName(left)).equals(singularize(canonicalCardName(right)));
	}

	private static String singularize(String value) {
		return value.endsWith("s") && value.length() > 3 ? value.substring(0, value.length() - 1) : value;
	}

	private static String canonicalCardName(String value) {
		return canonicalCardName(value, false);
	}

	private static String canonicalCardName(String value, boolean primaryOnly) {
		String source = value == null ? "" : value;
		if (primaryOnly) source = source.replaceFirst("\\s+-\\s+.*$", "");
		String normalized = normalizeText(source);
		if (normalized.startsWith("m ")) normalized = normalized.substring(2);
		return normalized.replaceAll(" ex$", "").trim();
	}

	private static String normalizeText(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT)
				.replace("&amp;", "&")
				.replaceAll("[^a-z0-9]+", " ").replaceAll("\\s+", " ").trim();
	}

	private static String required(String value, String fieldName) {
		if (value == null || value.isBlank()) throw new IllegalStateException("TCG Collector " + fieldName + " is required");
		return value.trim();
	}

	private static String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}

	public enum MatchConfidence {
		NUMBER_AND_NAME,
		UNIQUE_NAME
	}

	public record ReferenceCardMatch(
			String referenceSetName,
			String cardName,
			String normalizedCollectorNumber,
			MatchConfidence confidence) {
	}

	private record CatalogFile(List<SourceSet> sets, String digest) {
	}

	private record SourceSet(String set, List<SourceCard> carte) {
	}

	private record SourceCard(String nome, String numero) {
	}

	private record ImportedSet(String name, String normalizedName, List<ImportedCard> cards) {
	}

	private record ImportedCard(String name, String number, String normalizedCollectorNumber) {
	}
}
