package com.example.cardmonitoring.pokemontcg;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TcgCollectorReferenceSetRepository extends JpaRepository<TcgCollectorReferenceSet, Long> {

	Optional<TcgCollectorReferenceSet> findByNormalizedName(String normalizedName);
}
