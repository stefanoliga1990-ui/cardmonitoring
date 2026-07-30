package com.example.cardmonitoring.pokemontcg;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TcgCollectorReferenceCardRepository extends JpaRepository<TcgCollectorReferenceCard, Long> {

	List<TcgCollectorReferenceCard> findByReferenceSetId(long referenceSetId);

	List<TcgCollectorReferenceCard> findByReferenceSetIdAndNormalizedCollectorNumber(
			long referenceSetId, String normalizedCollectorNumber);
}
