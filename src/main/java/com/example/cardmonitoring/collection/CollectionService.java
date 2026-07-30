package com.example.cardmonitoring.collection;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cardmonitoring.catalog.CatalogBlueprint;
import com.example.cardmonitoring.catalog.CatalogExpansion;
import com.example.cardmonitoring.catalog.CatalogService;
import com.example.cardmonitoring.catalog.CollectorNumberParser;
import com.example.cardmonitoring.monitoring.Monitoring;
import com.example.cardmonitoring.monitoring.MonitoringRepository;
import com.example.cardmonitoring.pokemontcg.CardImage;
import com.example.cardmonitoring.pokemontcg.StoredCardImage;
import com.example.cardmonitoring.pokemontcg.CardImageRepository;
import com.example.cardmonitoring.user.AppUser;
import com.example.cardmonitoring.user.AppUserRepository;

@Service
public class CollectionService {

	private static final String IMAGE_SOURCE = "POKEMON_TCG_API";

	private final CatalogService catalogService;
	private final AppUserRepository appUserRepository;
	private final CollectionSetRepository collectionSetRepository;
	private final CollectionCardRepository collectionCardRepository;
	private final UserCollectionRepository userCollectionRepository;
	private final UserCollectionCardRepository userCollectionCardRepository;
	private final CardImageRepository cardImageRepository;
	private final MonitoringRepository monitoringRepository;

	public CollectionService(
			CatalogService catalogService,
			AppUserRepository appUserRepository,
			CollectionSetRepository collectionSetRepository,
			CollectionCardRepository collectionCardRepository,
			UserCollectionRepository userCollectionRepository,
			UserCollectionCardRepository userCollectionCardRepository,
			CardImageRepository cardImageRepository,
			MonitoringRepository monitoringRepository) {
		this.catalogService = catalogService;
		this.appUserRepository = appUserRepository;
		this.collectionSetRepository = collectionSetRepository;
		this.collectionCardRepository = collectionCardRepository;
		this.userCollectionRepository = userCollectionRepository;
		this.userCollectionCardRepository = userCollectionCardRepository;
		this.cardImageRepository = cardImageRepository;
		this.monitoringRepository = monitoringRepository;
	}

	@Transactional(readOnly = true)
	public List<CollectionSummaryResponse> findActive(long ownerId) {
		return userCollectionRepository.findByOwnerIdAndActiveTrueOrderByCreatedAtDesc(ownerId).stream()
				.map(this::toSummary)
				.toList();
	}

	@Transactional
	public CollectionDetailResponse create(long ownerId, CreateCollectionRequest request) {
		if (request == null || request.expansionId() <= 0) {
			throw new IllegalArgumentException("expansionId is required");
		}
		AppUser owner = requireUser(ownerId);
		CollectionSet collectionSet = ensureCollectionSet(request.expansionId());
		Optional<UserCollection> existingUserCollection = userCollectionRepository
				.findByOwnerIdAndCollectionSetId(ownerId, collectionSet.getId());
		boolean alreadyPresent = existingUserCollection.isPresent();
		UserCollection userCollection = existingUserCollection
				.map(existing -> {
					existing.reactivate();
					return existing;
				})
				.orElseGet(() -> new UserCollection(owner, collectionSet, Instant.now()));
		userCollection = userCollectionRepository.save(userCollection);
		return toDetail(userCollection, alreadyPresent);
	}

	@Transactional(readOnly = true)
	public CollectionDetailResponse findById(long ownerId, long collectionId) {
		return toDetail(requireUserCollection(ownerId, collectionId));
	}

	@Transactional
	public void delete(long ownerId, long collectionId) {
		UserCollection userCollection = requireUserCollection(ownerId, collectionId);
		userCollectionRepository.delete(userCollection);
	}

	@Transactional
	public CollectionDetailResponse updateCardOwnership(
			long ownerId,
			long collectionId,
			long cardId,
			UpdateCollectionCardRequest request) {
		UserCollection userCollection = requireUserCollection(ownerId, collectionId);
		CollectionCard collectionCard = collectionCardRepository
				.findByIdAndCollectionSetId(cardId, userCollection.getCollectionSet().getId())
				.orElseThrow(() -> new CollectionNotFoundException("Collection card not found: " + cardId));
		Instant now = Instant.now();
		UserCollectionCard userCard = userCollectionCardRepository
				.findByUserCollectionIdAndCollectionCardId(userCollection.getId(), collectionCard.getId())
				.orElseGet(() -> new UserCollectionCard(userCollection, collectionCard, now));
		userCard.setOwned(request != null && request.owned(), now);
		userCollectionCardRepository.save(userCard);
		return toDetail(userCollection);
	}

	private CollectionSet ensureCollectionSet(long expansionId) {
		CatalogExpansion expansion = catalogService.getPokemonExpansions().stream()
				.filter(candidate -> candidate.id() == expansionId)
				.findFirst()
				.orElseThrow(() -> new CollectionNotFoundException("Pokemon expansion not found: " + expansionId));
		List<CatalogBlueprint> blueprints = catalogService.getPokemonBlueprints(expansionId);
		if (blueprints.isEmpty()) {
			throw new CollectionNotFoundException("Pokemon expansion has no eligible cards: " + expansionId);
		}
		Instant now = Instant.now();
		CollectionSet collectionSet = collectionSetRepository.findByExpansionId(expansionId)
				.map(existing -> {
					existing.updateCatalog(expansion.name(), expansion.code(), blueprints.size(), now);
					return existing;
				})
				.orElseGet(() -> new CollectionSet(expansion.id(), expansion.name(), expansion.code(), blueprints.size(), now));
		collectionSet = collectionSetRepository.saveAndFlush(collectionSet);
		upsertCollectionCards(collectionSet, blueprints, now);
		return collectionSet;
	}

	private void upsertCollectionCards(CollectionSet collectionSet, List<CatalogBlueprint> blueprints, Instant now) {
		Map<Long, CollectionCard> existingCards = collectionCardRepository
				.findByCollectionSetIdOrderBySortOrderAsc(collectionSet.getId())
				.stream()
				.collect(Collectors.toMap(CollectionCard::getBlueprintId, card -> card));
		int index = 0;
		for (CatalogBlueprint blueprint : blueprints.stream().sorted(COLLECTION_CARD_ORDER).toList()) {
			String collectorNumber = CollectorNumberParser.fromVersion(blueprint.version()).orElse(null);
			CollectionCard existingCard = existingCards.get(blueprint.id());
			if (existingCard == null) {
				collectionCardRepository.save(new CollectionCard(
						collectionSet,
						collectionSet.getExpansionId(),
						blueprint.id(),
						blueprint.name(),
						blueprint.version(),
						collectorNumber,
						index,
						now));
			}
			else {
				existingCard.refresh(blueprint.name(), blueprint.version(), collectorNumber, index, now);
			}
			index += 1;
		}
		collectionCardRepository.flush();
	}

	private CollectionDetailResponse toDetail(UserCollection userCollection) {
		return toDetail(userCollection, false);
	}

	private CollectionDetailResponse toDetail(UserCollection userCollection, boolean alreadyPresent) {
		CollectionSet collectionSet = userCollection.getCollectionSet();
		List<CollectionCard> cards = collectionCardRepository
				.findByCollectionSetIdOrderBySortOrderAsc(collectionSet.getId());
		Map<Long, CardImage> images = cardImageRepository
				.findByExpansionIdAndImageSource(collectionSet.getExpansionId(), IMAGE_SOURCE)
				.stream()
				.filter(StoredCardImage::hasImage)
				.collect(Collectors.toMap(
						StoredCardImage::getBlueprintId,
						StoredCardImage::toCardImage,
						(first, second) -> first,
						HashMap::new));
		Set<Long> ownedCardIds = userCollectionCardRepository.findByUserCollectionId(userCollection.getId()).stream()
				.filter(UserCollectionCard::isOwned)
				.map(card -> card.getCollectionCard().getId())
				.collect(Collectors.toCollection(HashSet::new));
		Set<Long> activeMonitoringBlueprintIds = monitoringRepository
				.findByOwnerIdAndActiveTrueAndExpansionId(userCollection.getOwner().getId(), collectionSet.getExpansionId())
				.stream()
				.map(Monitoring::getBlueprintId)
				.collect(Collectors.toSet());
		List<CollectionCardResponse> cardResponses = cards.stream()
				.sorted(Comparator.comparingInt(CollectionCard::getSortOrder))
				.map(card -> toCardResponse(card, images.get(card.getBlueprintId()), ownedCardIds,
						activeMonitoringBlueprintIds))
				.toList();
		return new CollectionDetailResponse(
				userCollection.getId(),
				collectionSet.getExpansionId(),
				collectionSet.getName(),
				collectionSet.getCode(),
				collectionSet.getCardCount(),
				collectionSet.getImageSyncStatus(),
				collectionSet.getLastError(),
				alreadyPresent,
				cardResponses);
	}

	private CollectionCardResponse toCardResponse(
			CollectionCard card,
			CardImage image,
			Set<Long> ownedCardIds,
			Set<Long> activeMonitoringBlueprintIds) {
		return new CollectionCardResponse(
				Objects.requireNonNull(card.getId(), "collection card id is required"),
				card.getExpansionId(),
				card.getBlueprintId(),
				card.getCardName(),
				card.getCardVersion(),
				card.getCollectorNumber(),
				image == null ? null : image.smallUrl(),
				image == null ? null : image.largeUrl(),
				ownedCardIds.contains(card.getId()),
				activeMonitoringBlueprintIds.contains(card.getBlueprintId()));
	}

	private CollectionSummaryResponse toSummary(UserCollection userCollection) {
		CollectionSet collectionSet = userCollection.getCollectionSet();
		return new CollectionSummaryResponse(
				userCollection.getId(),
				collectionSet.getExpansionId(),
				collectionSet.getName(),
				collectionSet.getCode(),
				collectionSet.getCardCount(),
				collectionSet.getImageSyncStatus(),
				userCollection.getCreatedAt());
	}

	private UserCollection requireUserCollection(long ownerId, long collectionId) {
		return userCollectionRepository.findByIdAndOwnerIdAndActiveTrue(collectionId, ownerId)
				.orElseThrow(() -> new CollectionNotFoundException("Collection not found: " + collectionId));
	}

	private AppUser requireUser(long ownerId) {
		return appUserRepository.findById(ownerId)
				.orElseThrow(() -> new CollectionNotFoundException("User not found"));
	}

	private static final Comparator<CatalogBlueprint> COLLECTION_CARD_ORDER = Comparator
			.comparing(CollectionService::hasNonStandardCollectorNumber)
			.thenComparingInt(CollectionService::numericCollectorNumber)
			.thenComparing(CatalogBlueprint::name, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(CatalogBlueprint::id);

	private static boolean hasNonStandardCollectorNumber(CatalogBlueprint blueprint) {
		return CollectorNumberParser.fromVersion(blueprint.version())
				.map(number -> !number.matches("\\d+"))
				.orElse(true);
	}

	private static int numericCollectorNumber(CatalogBlueprint blueprint) {
		return CollectorNumberParser.fromVersion(blueprint.version())
				.filter(number -> number.matches("\\d+"))
				.map(Integer::parseInt)
				.orElse(Integer.MAX_VALUE);
	}
}
