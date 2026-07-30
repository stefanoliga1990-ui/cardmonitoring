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
import com.example.cardmonitoring.catalog.CollectorNumberParser;

@Service
public class CardImageService {

	private static final Logger LOGGER = LoggerFactory.getLogger(CardImageService.class);
	private static final String SOURCE = "POKEMON_TCG_API";
	private static final Duration IMAGE_URL_MAXIMUM_AGE = Duration.ofDays(120);
	private static final Pattern TRAILING_EX = Pattern.compile("(?i)\\s+ex\\s*$");
	private static final String NO_COLLECTOR_NUMBER_CACHE_KEY = "__NO_COLLECTOR_NUMBER__";

	private final PokemonTcgClient pokemonTcgClient;
	private final PokemonTcgSetImageService pokemonTcgSetImageService;
	private final TcgCollectorReferenceCatalogService referenceCatalogService;
	private final CardImageRepository cardImageRepository;

	public CardImageService(PokemonTcgClient pokemonTcgClient,
			PokemonTcgSetImageService pokemonTcgSetImageService,
			TcgCollectorReferenceCatalogService referenceCatalogService,
			CardImageRepository cardImageRepository) {
		this.pokemonTcgClient = pokemonTcgClient;
		this.pokemonTcgSetImageService = pokemonTcgSetImageService;
		this.referenceCatalogService = referenceCatalogService;
		this.cardImageRepository = cardImageRepository;
	}

	public Optional<CardImage> resolve(CatalogCard card) {
		LOGGER.info(
				"Starting card image lookup: blueprintId={}, expansionId={}, cardName='{}', cardVersion='{}', expansionName='{}'",
				card.blueprintId(), card.expansionId(), card.cardName(), card.cardVersion(), card.expansionName());
		String collectorNumber = CollectorNumberParser.fromVersion(card.cardVersion()).orElse(null);
		String cacheKey = collectorNumber == null ? NO_COLLECTOR_NUMBER_CACHE_KEY : collectorNumber;
		if (collectorNumber == null) {
			LOGGER.info("Card image lookup has no reliable collector number; using name-only fallbacks: blueprintId={}, cardVersion='{}'",
					card.blueprintId(), card.cardVersion());
		}

		Instant now = Instant.now();
		Optional<StoredCardImage> storedImage = findStoredImage(card, cacheKey);
		if (storedImage.isPresent()) {
			return resolveStoredImage(card, cacheKey, collectorNumber, storedImage.get(), now);
		}

		LOGGER.info("Card image persistent cache miss: blueprintId={}, expansionId={}, collectorNumber={}",
				card.blueprintId(), card.expansionId(), cacheKey);
		Optional<CardImage> resolvedImage = resolveFromPokemonTcg(card, collectorNumber, null);
		return resolvedImage.map(image -> saveNewImage(card, cacheKey, image, now));
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

	/** Used by the maintenance backfill to repair cache rows created by older, number-only matching. */
	public Optional<Boolean> isStoredImageCompatible(CatalogCard card, String externalCardId) {
		Optional<TcgCollectorReferenceCatalogService.ReferenceCardMatch> referenceMatch = referenceCatalogService.findMatch(card);
		String expectedNumber = referenceMatch
				.map(TcgCollectorReferenceCatalogService.ReferenceCardMatch::normalizedCollectorNumber)
				.filter(StringUtils::hasText)
				.orElseGet(() -> CollectorNumberParser.fromVersion(card.cardVersion())
						.map(CardImageService::normalizeNumber).orElse(null));
		String expectedName = referenceMatch
				.map(TcgCollectorReferenceCatalogService.ReferenceCardMatch::cardName)
				.orElse(card.cardName());
		return pokemonTcgSetImageService.isStoredImageCompatible(card, externalCardId, expectedName, expectedNumber);
	}

	private Optional<CardImage> resolveStoredImage(
			CatalogCard card,
			String cacheKey,
			String collectorNumber,
			StoredCardImage storedImage,
			Instant now) {
		if (!isStoredImageCompatible(card, storedImage.getExternalCardId()).orElse(true)) {
			LOGGER.info(
					"Removing incompatible cached card image before resolving through the reference catalog: id={}, blueprintId={}, expansionId={}, externalCardId={}",
					storedImage.getId(), card.blueprintId(), card.expansionId(), storedImage.getExternalCardId());
			cardImageRepository.delete(storedImage);
			return resolveFromPokemonTcg(card, collectorNumber, null)
					.map(image -> saveNewImage(card, cacheKey, image, now));
		}
		if (storedImage.isFresh(now, IMAGE_URL_MAXIMUM_AGE)) {
			storedImage.recordUse(now);
			cardImageRepository.save(storedImage);
			LOGGER.info(
					"Card image persistent cache hit: blueprintId={}, expansionId={}, collectorNumber={}, imageSource={}, lastVerifiedAt={}",
				card.blueprintId(), card.expansionId(), cacheKey, storedImage.getImageSource(),
					storedImage.getLastVerifiedAt());
			return Optional.of(storedImage.toCardImage());
		}

		LOGGER.info(
				"Card image persistent cache entry requires refresh: id={}, blueprintId={}, expansionId={}, collectorNumber={}, status={}, lastVerifiedAt={}, failures={}",
			storedImage.getId(), card.blueprintId(), card.expansionId(), cacheKey, storedImage.getStatus(),
				storedImage.getLastVerifiedAt(), storedImage.getVerificationFailures());
		Optional<CardImage> refreshedImage = resolveFromPokemonTcg(card, collectorNumber, storedImage.getExternalCardId());
		if (refreshedImage.isPresent()) {
			storedImage.refresh(card, cacheKey, refreshedImage.get(), now);
			cardImageRepository.save(storedImage);
			LOGGER.info(
					"Card image persistent cache refreshed: id={}, blueprintId={}, expansionId={}, collectorNumber={}, imageSource={}",
				storedImage.getId(), card.blueprintId(), card.expansionId(), cacheKey,
					refreshedImage.get().source());
			return refreshedImage;
		}

		storedImage.recordVerificationFailure(now);
		cardImageRepository.save(storedImage);
		if (storedImage.hasImage()) {
			LOGGER.info(
					"Card image refresh failed, using stored stale image: id={}, blueprintId={}, expansionId={}, collectorNumber={}, status={}",
				storedImage.getId(), card.blueprintId(), card.expansionId(), cacheKey,
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
		Optional<TcgCollectorReferenceCatalogService.ReferenceCardMatch> referenceMatch = referenceCatalogService.findMatch(card);
		String expectedName = referenceMatch
				.map(TcgCollectorReferenceCatalogService.ReferenceCardMatch::cardName)
				.orElse(card.cardName());
		String expectedNumber = referenceMatch
				.map(TcgCollectorReferenceCatalogService.ReferenceCardMatch::normalizedCollectorNumber)
				.filter(StringUtils::hasText)
				.orElse(collectorNumber);
		if (referenceMatch.isPresent()) {
			TcgCollectorReferenceCatalogService.ReferenceCardMatch match = referenceMatch.get();
			LOGGER.info(
					"Resolved CardTrader card through TCG Collector reference: blueprintId={}, referenceSet='{}', referenceName='{}', referenceNumber={}, confidence={}",
					card.blueprintId(), match.referenceSetName(), match.cardName(), match.normalizedCollectorNumber(),
					match.confidence());
		}
		if (StringUtils.hasText(externalCardId)) {
			Optional<PokemonTcgCardCandidate> cardById = pokemonTcgClient.findCardById(externalCardId);
			if (cardById.isPresent()
					&& (expectedNumber == null || numberMatches(expectedNumber, cardById.get().number()))
					&& PokemonTcgSetImageService.namesCompatible(expectedName, cardById.get().name())) {
				PokemonTcgCardCandidate candidate = cardById.get();
				LOGGER.info(
						"Selected Pokemon TCG image by external id: blueprintId={}, candidateId={}, candidateName='{}', candidateNumber={}, candidateSet='{}', hasSmallImage={}, hasLargeImage={}",
						card.blueprintId(), candidate.id(), candidate.name(), candidate.number(), candidate.setName(),
						candidate.smallImageUrl() != null, candidate.largeImageUrl() != null);
				return Optional.of(toCardImage(candidate));
			}
			LOGGER.info(
					"Pokemon TCG external id refresh did not return a compatible image, falling back to search: blueprintId={}, externalCardId={}, collectorNumber={}",
					card.blueprintId(), externalCardId, expectedNumber);
		}
		if (expectedNumber != null) {
			List<PokemonTcgCardCandidate> setCandidates = pokemonTcgSetImageService.findCandidates(card, expectedNumber);
			if (setCandidates.size() == 1
					&& PokemonTcgSetImageService.namesCompatible(expectedName, setCandidates.get(0).name())) {
				PokemonTcgCardCandidate candidate = setCandidates.get(0);
				LOGGER.info("Selected Pokemon TCG image by mapped set and number: blueprintId={}, candidateId={}, set='{}', number={}",
						card.blueprintId(), candidate.id(), candidate.setName(), candidate.number());
				return Optional.of(toCardImage(candidate));
			}
			if (setCandidates.size() > 1) {
				Optional<PokemonTcgCardCandidate> selectedSetCandidate = uniqueNameMatch(expectedName, setCandidates);
				if (selectedSetCandidate.isPresent()) return Optional.of(toCardImage(selectedSetCandidate.get()));
				LOGGER.info("Mapped Pokemon TCG set has ambiguous collector number; falling back to name search: blueprintId={}, candidates={}",
						card.blueprintId(), setCandidates.size());
			}
		}

		List<PokemonTcgCardCandidate> sameSetNameCandidates = referenceMatch.isPresent()
				? pokemonTcgSetImageService.findCandidatesByName(card, expectedName)
				: pokemonTcgSetImageService.findCandidatesByName(card);
		Optional<PokemonTcgCardCandidate> sameSetNameCandidate = uniqueImageCandidate(sameSetNameCandidates);
		if (sameSetNameCandidate.isPresent()) {
			PokemonTcgCardCandidate candidate = sameSetNameCandidate.get();
			LOGGER.info("Selected Pokemon TCG image by mapped set and unique name: blueprintId={}, candidateId={}, set='{}', number={}",
					card.blueprintId(), candidate.id(), candidate.setName(), candidate.number());
			return Optional.of(toCardImage(candidate));
		}

		if (referenceMatch.isEmpty() && collectorNumber != null) {
			String query = query(card.cardName(), collectorNumber);
			LOGGER.info("Searching Pokemon TCG image candidates: blueprintId={}, collectorNumber={}, query={}",
					card.blueprintId(), collectorNumber, query);
			List<PokemonTcgCardCandidate> candidates = pokemonTcgClient.searchCards(query);
			if (candidates.isEmpty()) {
				String hyphenatedExName = hyphenatedExName(card.cardName());
				if (hyphenatedExName != null) {
					String fallbackQuery = query(hyphenatedExName, collectorNumber);
					LOGGER.info("Pokemon TCG image search returned no candidates; retrying with hyphenated EX name: blueprintId={}, collectorNumber={}, query={}",
							card.blueprintId(), collectorNumber, fallbackQuery);
					candidates = pokemonTcgClient.searchCards(fallbackQuery);
				}
			}
			LOGGER.info("Pokemon TCG returned {} image candidate(s): blueprintId={}, collectorNumber={}",
					candidates.size(), card.blueprintId(), collectorNumber);
			Optional<PokemonTcgCardCandidate> selected = candidates.stream()
					.filter(candidate -> numberMatches(collectorNumber, candidate.number()))
					.filter(candidate -> PokemonTcgSetImageService.namesCompatible(card.cardName(), candidate.name()))
					.min(Comparator
							.comparingInt((PokemonTcgCardCandidate candidate) -> matchScore(card, collectorNumber, candidate))
							.reversed()
							.thenComparing(PokemonTcgCardCandidate::id));
			if (selected.isPresent()) {
				PokemonTcgCardCandidate candidate = selected.get();
				LOGGER.info("Selected Pokemon TCG image: blueprintId={}, candidateId={}, candidateName='{}', candidateNumber={}, candidateSet='{}'",
						card.blueprintId(), candidate.id(), candidate.name(), candidate.number(), candidate.setName());
				return Optional.of(toCardImage(candidate));
			}
		}

		Optional<PokemonTcgCardCandidate> globalNameCandidate = pokemonTcgClient.searchSingleCard(nameQuery(expectedName))
				.filter(candidate -> PokemonTcgSetImageService.namesCompatible(expectedName, candidate.name()))
				.filter(CardImageService::hasImage);
		if (globalNameCandidate.isPresent()) {
			PokemonTcgCardCandidate candidate = globalNameCandidate.get();
			LOGGER.info("Selected Pokemon TCG image by globally unique name: blueprintId={}, candidateId={}, set='{}', number={}",
					card.blueprintId(), candidate.id(), candidate.setName(), candidate.number());
			return Optional.of(toCardImage(candidate));
		}
		LOGGER.info("No Pokemon TCG image selected after number and name fallbacks: blueprintId={}, collectorNumber={}",
				card.blueprintId(), collectorNumber);
		return Optional.empty();
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

	private static Optional<PokemonTcgCardCandidate> uniqueNameMatch(
			String expectedName, List<PokemonTcgCardCandidate> candidates) {
		List<PokemonTcgCardCandidate> matching = candidates.stream()
				.filter(candidate -> PokemonTcgSetImageService.namesCompatible(expectedName, candidate.name()))
				.toList();
		return matching.size() == 1 ? Optional.of(matching.get(0)) : Optional.empty();
	}

	private static String query(String cardName, String collectorNumber) {
		return "name:" + quoted(cardName) + " number:" + normalizeSearchNumber(collectorNumber);
	}

	private static String nameQuery(String cardName) {
		return "name:" + quoted(cardName);
	}

	private static Optional<PokemonTcgCardCandidate> uniqueImageCandidate(List<PokemonTcgCardCandidate> candidates) {
		List<PokemonTcgCardCandidate> withImages = candidates.stream().filter(CardImageService::hasImage).toList();
		return withImages.size() == 1 ? Optional.of(withImages.get(0)) : Optional.empty();
	}

	private static boolean hasImage(PokemonTcgCardCandidate candidate) {
		return candidate.smallImageUrl() != null || candidate.largeImageUrl() != null;
	}

	private static String hyphenatedExName(String cardName) {
		if (!StringUtils.hasText(cardName) || !TRAILING_EX.matcher(cardName).find()) {
			return null;
		}
		return TRAILING_EX.matcher(cardName).replaceFirst("-EX");
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
