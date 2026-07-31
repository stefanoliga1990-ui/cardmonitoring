package com.example.cardmonitoring.pokemontcg;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TcgReferencePokemonSetMappingPersistenceTest {

	@Autowired
	private TcgCollectorReferenceSetRepository referenceSetRepository;

	@Autowired
	private TcgReferencePokemonSetMappingRepository mappingRepository;

	@Test
	void persistsMappedSetStatusUsingTheFlywaySchema() {
		TcgCollectorReferenceSet referenceSet = referenceSetRepository.saveAndFlush(
				new TcgCollectorReferenceSet("Persistence test set", "persistence-test-set"));

		mappingRepository.saveAndFlush(TcgReferencePokemonSetMapping.mapped(
				9_999_999L, referenceSet.getId(), "base1", Instant.parse("2026-07-31T10:00:00Z")));

		assertThat(mappingRepository.findById(9_999_999L))
				.get()
				.extracting(TcgReferencePokemonSetMapping::isMapped,
						TcgReferencePokemonSetMapping::getPokemonTcgSetId)
				.containsExactly(true, "base1");

		mappingRepository.saveAndFlush(TcgReferencePokemonSetMapping.unmappable(
				9_999_998L, referenceSet.getId(), Instant.parse("2026-07-31T10:00:01Z")));
		assertThat(mappingRepository.findById(9_999_998L))
				.get()
				.extracting(TcgReferencePokemonSetMapping::isMapped,
						TcgReferencePokemonSetMapping::getPokemonTcgSetId)
				.containsExactly(false, null);
	}
}
