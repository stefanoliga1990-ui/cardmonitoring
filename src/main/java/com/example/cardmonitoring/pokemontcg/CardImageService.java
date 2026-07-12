package com.example.cardmonitoring.pokemontcg;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.cardmonitoring.catalog.CatalogCard;

@Service
public class CardImageService {

	private static final Logger LOGGER = LoggerFactory.getLogger(CardImageService.class);
	private static final String SOURCE = "POKEMON_TCG_API";
	private static final Duration IMAGE_URL_MAXIMUM_AGE = Duration.ofDays(120);
	private static final Pattern COLLECTOR_NUMBER_PATTERN = Pattern.compile(
			"(?i)(?:^|\\s|\\|)([a-z]*\\d+[a-z]*)\\s*(?:/\\s*([a-z]*\\d+[a-z]*))?");

	private final PokemonTcgClient pokemonTcgClient;
	private final CardImageRepository cardImageRepository;

	public CardImageService(PokemonTcgClient pokemonTcgClient, CardImageRepository cardImageRepository) {
		this.pokemonTcgClient = pokemonTcgClient;
		this.cardImageRepository = cardImageRepository;
	}

	public Optional<CardImage> resolve(CatalogCard card) {
		LOGGER.info(
				"Starting card image lookup: blueprintId={}, expansionId={}, cardName='{}', cardVersion='{}', expansionName='{}'",
				card.blueprintId(), card.expansionId(), card.cardName(), card.cardVersion(), card.expansionName());
		String collectorNumber = collectorNumber(card.cardVersion()).orElse(null);
		if (collectorNumber == null) {
			LOGGER.info(
					"Card image lookup skipped: collector number not found in version. blueprintId={}, cardVersion='{}'",
					card.blueprintId(), card.cardVersion());
			return Optional.empty();
		}

		Instant now = Instant.now();
		Optional<StoredCardImage> storedImage = findStoredImage(card, collectorNumber);
		if (storedImage.isPresent()) {
			return resolveStoredImage(card, collectorNumber, storedImage.get(), now);
		}

		LOGGER.info("Card image persistent cache miss: blueprintId={}, expansionId={}, collectorNumber={}",
				card.blueprintId(), card.expansionId(), collectorNumber);
		Optional<CardImage> resolvedImage = resolveFromPokemonTcg(card, collectorNumber, null);
		return resolvedImage.map(image -> saveNewImage(card, collectorNumber, image, now));
	}

	public Optional<CardImage> cacheResolvedImage(CatalogCard card, String collectorNumber, CardImage image) {
		if (image == null || !image.hasImage() || !StringUtils.hasText(collectorNumber)) {
			LOGGER.info(
					"Card image cache save skipped: blueprintId={}, expansionId={}, hasImage={}, collectorNumberPresent={}",
					card.blueprintId(), card.expansionId(), image != null && image.hasImage(),
					StringUtils.hasText(collectorNumber));
			return Optional.empty();
		}
		Instant now = Instant.now();
		String normalizedCollectorNumber = normalizeNumber(collectorNumber);
		Optional<StoredCardImage> storedImage = findStoredImage(card, normalizedCollectorNumber);
		if (storedImage.isPresent()) {
			StoredCardImage existing = storedImage.get();
			existing.refresh(card, normalizedCollectorNumber, image, now);
			cardImageRepository.save(existing);
			LOGGER.info(
					"Card image cached from resolved candidate: id={}, blueprintId={}, expansionId={}, collectorNumber={}, imageSource={}",
					existing.getId(), card.blueprintId(), card.expansionId(), normalizedCollectorNumber,
					image.source());
			return Optional.of(existing.toCardImage());
		}
		return Optional.of(saveNewImage(card, normalizedCollectorNumber, image, now));
	}

	private Optional<CardImage> resolveStoredImage(
			CatalogCard card,
			String collectorNumber,
			StoredCardImage storedImage,
			Instant now) {
		if (storedImage.isFresh(now, IMAGE_URL_MAXIMUM_AGE)) {
			storedImage.recordUse(now);
			cardImageRepository.save(storedImage);
			LOGGER.info(
					"Card image persistent cache hit: blueprintId={}, expansionId={}, collectorNumber={}, imageSource={}, lastVerifiedAt={}",
					card.blueprintId(), card.expansionId(), collectorNumber, storedImage.getImageSource(),
					storedImage.getLastVerifiedAt());
			return Optional.of(storedImage.toCardImage());
		}

		LOGGER.info(
				"Card image persistent cache entry requires refresh: id={}, blueprintId={}, expansionId={}, collectorNumber={}, status={}, lastVerifiedAt={}, failures={}",
				storedImage.getId(), card.blueprintId(), card.expansionId(), collectorNumber, storedImage.getStatus(),
				storedImage.getLastVerifiedAt(), storedImage.getVerificationFailures());
		Optional<CardImage> refreshedImage = resolveFromPokemonTcg(card, collectorNumber, storedImage.getExternalCardId());
		if (refreshedImage.isPresent()) {
			storedImage.refresh(card, collectorNumber, refreshedImage.get(), now);
			cardImageRepository.save(storedImage);
			LOGGER.info(
					"Card image persistent cache refreshed: id={}, blueprintId={}, expansionId={}, collectorNumber={}, imageSource={}",
					storedImage.getId(), card.blueprintId(), card.expansionId(), collectorNumber,
					refreshedImage.get().source());
			return refreshedImage;
		}

		storedImage.recordVerificationFailure(now);
		cardImageRepository.save(storedImage);
		if (storedImage.hasImage()) {
			LOGGER.info(
					"Card image refresh failed, using stored stale image: id={}, blueprintId={}, expansionId={}, collectorNumber={}, status={}",
					storedImage.getId(), card.blueprintId(), card.expansionId(), collectorNumber,
					storedImage.getStatus());
			return Optional.of(storedImage.toCardImage());
		}
		LOGGER.info("Card image refresh failed and no stored image is usable: id={}, blueprintId={}, expansionId={}",
				storedImage.getId(), card.blueprintId(), card.expansionId());
		return Optional.empty();
	}

	private Optional<StoredCardImage> findStoredImage(CatalogCard card, String collectorNumber) {
		return cardImageRepository.findByExpansionIdAndBlueprintIdAndCollectorNumberAndImageSource(
				card.expansionId(),
				card.blueprintId(),
				normalizeNumber(collectorNumber),
				SOURCE);
	}

	private Optional<CardImage> resolveFromPokemonTcg(
			CatalogCard card,
			String collectorNumber,
			String externalCardId) {
		if (StringUtils.hasText(externalCardId)) {
			Optional<PokemonTcgCardCandidate> cardById = pokemonTcgClient.findCardById(externalCardId);
			if (cardById.isPresent() && numberMatches(collectorNumber, cardById.get().number())) {
				PokemonTcgCardCandidate candidate = cardById.get();
				LOGGER.info(
						"Selected Pokemon TCG image by external id: blueprintId={}, candidateId={}, candidateName='{}', candidateNumber={}, candidateSet='{}', hasSmallImage={}, hasLargeImage={}",
						card.blueprintId(), candidate.id(), candidate.name(), candidate.number(), candidate.setName(),
						candidate.smallImageUrl() != null, candidate.largeImageUrl() != null);
				return Optional.of(toCardImage(candidate));
			}
			LOGGER.info(
					"Pokemon TCG external id refresh did not return a compatible image, falling back to search: blueprintId={}, externalCardId={}, collectorNumber={}",
					card.blueprintId(), externalCardId, collectorNumber);
		}
		String query = query(card.cardName(), collectorNumber);
		LOGGER.info("Searching Pokemon TCG image candidates: blueprintId={}, collectorNumber={}, query={}",
				card.blueprintId(), collectorNumber, query);
		List<PokemonTcgCardCandidate> candidates = pokemonTcgClient.searchCards(query);
		LOGGER.info("Pokemon TCG returned {} image candidate(s): blueprintId={}, collectorNumber={}",
				candidates.size(), card.blueprintId(), collectorNumber);
		candidates.forEach(candidate -> LOGGER.debug(
				"Pokemon TCG candidate: blueprintId={}, candidateId={}, name='{}', number={}, setName='{}', score={}, hasSmallImage={}, hasLargeImage={}",
				card.blueprintId(), candidate.id(), candidate.name(), candidate.number(), candidate.setName(),
				matchScore(card, collectorNumber, candidate), candidate.smallImageUrl() != null,
				candidate.largeImageUrl() != null));
		Optional<PokemonTcgCardCandidate> selected = candidates.stream()
				.filter(candidate -> numberMatches(collectorNumber, candidate.number()))
				.min(Comparator
						.comparingInt((PokemonTcgCardCandidate candidate) -> matchScore(card, collectorNumber, candidate))
						.reversed()
						.thenComparing(PokemonTcgCardCandidate::id));
		if (selected.isEmpty()) {
			LOGGER.info("No Pokemon TCG image selected: blueprintId={}, collectorNumber={}, candidateCount={}",
					card.blueprintId(), collectorNumber, candidates.size());
			return Optional.empty();
		}
		PokemonTcgCardCandidate candidate = selected.get();
		LOGGER.info(
				"Selected Pokemon TCG image: blueprintId={}, candidateId={}, candidateName='{}', candidateNumber={}, candidateSet='{}', hasSmallImage={}, hasLargeImage={}",
				card.blueprintId(), candidate.id(), candidate.name(), candidate.number(), candidate.setName(),
				candidate.smallImageUrl() != null, candidate.largeImageUrl() != null);
		return Optional.of(toCardImage(candidate));
	}

	private CardImage saveNewImage(CatalogCard card, String collectorNumber, CardImage image, Instant now) {
		StoredCardImage storedImage = new StoredCardImage(card, normalizeNumber(collectorNumber), image, now);
		try {
			StoredCardImage savedImage = cardImageRepository.saveAndFlush(storedImage);
			LOGGER.info(
					"Card image saved to persistent cache: id={}, blueprintId={}, expansionId={}, collectorNumber={}, imageSource={}",
					savedImage.getId(), card.blueprintId(), card.expansionId(), collectorNumber, image.source());
			return savedImage.toCardImage();
		}
		catch (DataIntegrityViolationException exception) {
			LOGGER.info(
					"Card image persistent cache already contains this identity, reusing existing row: blueprintId={}, expansionId={}, collectorNumber={}, imageSource={}",
					card.blueprintId(), card.expansionId(), collectorNumber, image.source());
			return findStoredImage(card, collectorNumber)
					.map(StoredCardImage::toCardImage)
					.orElse(image);
		}
	}

	private static CardImage toCardImage(PokemonTcgCardCandidate candidate) {
		return new CardImage(candidate.smallImageUrl(), candidate.largeImageUrl(), SOURCE, candidate.id());
	}

	private static String query(String cardName, String collectorNumber) {
		return "name:" + quoted(cardName) + " number:" + normalizeSearchNumber(collectorNumber);
	}

	private static String quoted(String value) {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private static int matchScore(CatalogCard card, String collectorNumber, PokemonTcgCardCandidate candidate) {
		int score = 0;
		if (equalsNormalized(card.cardName(), candidate.name())) {
			score += 100;
		}
		if (numberMatches(collectorNumber, candidate.number())) {
			score += 80;
		}
		if (equalsNormalized(card.expansionName(), candidate.setName())) {
			score += 50;
		}
		else if (containsNormalized(card.expansionName(), candidate.setName())
				|| containsNormalized(candidate.setName(), card.expansionName())) {
			score += 20;
		}
		return score;
	}

	private static Optional<String> collectorNumber(String version) {
		if (version == null || version.isBlank()) {
			return Optional.empty();
		}
		Matcher matcher = COLLECTOR_NUMBER_PATTERN.matcher(version);
		while (matcher.find()) {
			String candidate = matcher.group(1);
			if (candidate != null && !candidate.isBlank()) {
				return Optional.of(candidate.toUpperCase());
			}
		}
		return Optional.empty();
	}

	private static boolean numberMatches(String expected, String actual) {
		return normalizeNumber(expected).equals(normalizeNumber(actual));
	}

	private static String normalizeSearchNumber(String value) {
		String normalized = normalizeNumber(value);
		return normalized.isBlank() ? value : normalized;
	}

	private static String normalizeNumber(String value) {
		if (value == null) {
			return "";
		}
		String normalized = value.trim().toUpperCase();
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

	private static String normalizeText(String value) {
		return value == null
				? ""
				: value.trim().toLowerCase().replaceAll("[^a-z0-9]+", " ").replaceAll("\\s+", " ").trim();
	}
}
