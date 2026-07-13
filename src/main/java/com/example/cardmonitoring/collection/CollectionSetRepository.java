package com.example.cardmonitoring.collection;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionSetRepository extends JpaRepository<CollectionSet, Long> {

	Optional<CollectionSet> findByExpansionId(long expansionId);
}
