package com.example.cardmonitoring.catalog;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.cardmonitoring.pokemontcg.CardImageRepository;

/** Removes cached URLs belonging to CardTrader expansions outside the curated catalogue. */
@Component
@Profile("!test")
@ConditionalOnProperty(
		name = "cardmonitoring.catalog.prune-image-cache-on-startup",
		havingValue = "true",
		matchIfMissing = true)
public class OfficialCatalogImageCachePruner implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(OfficialCatalogImageCachePruner.class);

	private final CatalogService catalogService;
	private final CardImageRepository cardImageRepository;

	public OfficialCatalogImageCachePruner(CatalogService catalogService, CardImageRepository cardImageRepository) {
		this.catalogService = catalogService;
		this.cardImageRepository = cardImageRepository;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		try {
			List<Long> officialExpansionIds = catalogService.getPokemonExpansions().stream()
					.map(CatalogExpansion::id)
					.toList();
			if (officialExpansionIds.isEmpty()) {
				LOGGER.warn("Image cache pruning skipped because the curated Pokémon catalogue is empty");
				return;
			}
			long deletedImages = cardImageRepository.deleteByExpansionIdNotIn(officialExpansionIds);
			LOGGER.info("Image cache pruning completed: curatedExpansions={}, deletedImages={}",
					officialExpansionIds.size(), deletedImages);
		}
		catch (RuntimeException exception) {
			// A temporary CardTrader failure must not prevent the application from starting.
			LOGGER.warn("Image cache pruning skipped because the curated catalogue could not be loaded: {}",
					exception.getMessage());
		}
	}
}
