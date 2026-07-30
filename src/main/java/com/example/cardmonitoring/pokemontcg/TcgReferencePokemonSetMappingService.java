package com.example.cardmonitoring.pokemontcg;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.cardmonitoring.catalog.CatalogCard;

/**
 * Resolves and persists the missing bridge between a CardTrader expansion,
 * its imported TCG Collector set and a Pokemon TCG API set. Two independent
 * card identities must agree before a whole-set mapping is accepted.
 */
@Service
public class TcgReferencePokemonSetMappingService {

	private static final Logger LOGGER = LoggerFactory.getLogger(TcgReferencePokemonSetMappingService.class);
	private static final int MAX_REFERENCE_PROBES = 5;

	private final TcgReferencePokemonSetMappingRepository mappingRepository;
	private final TcgCollectorReferenceCatalogService referenceCatalogService;
	private final PokemonTcgClient pokemonTcgClient;

	public TcgReferencePokemonSetMappingService(
			TcgReferencePokemonSetMappingRepository mappingRepository,
			TcgCollectorReferenceCatalogService referenceCatalogService,
			PokemonTcgClient pokemonTcgClient) {
		this.mappingRepository = mappingRepository;
		this.referenceCatalogService = referenceCatalogService;
		this.pokemonTcgClient = pokemonTcgClient;
	}

	public Optional<String> resolve(CatalogCard card, TcgCollectorReferenceCatalogService.ReferenceCardMatch match) {
		Optional<TcgReferencePokemonSetMapping> stored = mappingRepository.findById(card.expansionId());
		if (stored.isPresent() && stored.get().appliesTo(match.referenceSetId())) {
			return stored.get().isMapped() ? Optional.of(stored.get().getPokemonTcgSetId()) : Optional.empty();
		}

		Map<String, Integer> votes = new HashMap<>();
		int probes = 0;
		for (TcgCollectorReferenceCatalogService.ReferenceCardIdentity identity
				: referenceCatalogService.findCards(match.referenceSetId())) {
			if (probes >= MAX_REFERENCE_PROBES) break;
			if (!StringUtils.hasText(identity.cardName()) || !StringUtils.hasText(identity.normalizedCollectorNumber())) continue;
			probes++;
			pokemonTcgClient.searchSingleCard(query(identity.cardName(), identity.normalizedCollectorNumber()))
					.filter(candidate -> StringUtils.hasText(candidate.setId()))
					.ifPresent(candidate -> votes.merge(candidate.setId(), 1, Integer::sum));
		}
		Optional<String> winner = votes.size() == 1
				? votes.entrySet().stream().filter(entry -> entry.getValue() >= 2).map(Map.Entry::getKey).findFirst()
				: Optional.empty();
		if (winner.isPresent()) {
			mappingRepository.save(TcgReferencePokemonSetMapping.mapped(
						card.expansionId(), match.referenceSetId(), winner.get(), Instant.now()));
			LOGGER.info("Mapped TCG Collector set to Pokemon TCG API set: expansionId={}, referenceSet='{}', pokemonTcgSetId={}, probes={}",
						card.expansionId(), match.referenceSetName(), winner.get(), probes);
			return winner;
		}

		mappingRepository.save(TcgReferencePokemonSetMapping.unmappable(
				card.expansionId(), match.referenceSetId(), Instant.now()));
		LOGGER.info("No reliable Pokemon TCG API set mapping for TCG Collector set: expansionId={}, referenceSet='{}', probes={}, votes={}",
				card.expansionId(), match.referenceSetName(), probes, votes);
		return Optional.empty();
	}

	private static String query(String cardName, String collectorNumber) {
		return "name:\"" + cardName.replace("\\", "\\\\").replace("\"", "\\\"")
				+ "\" number:" + collectorNumber;
	}
}
