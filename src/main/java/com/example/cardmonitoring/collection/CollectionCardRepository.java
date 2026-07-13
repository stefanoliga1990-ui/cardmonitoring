package com.example.cardmonitoring.collection;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionCardRepository extends JpaRepository<CollectionCard, Long> {

	List<CollectionCard> findByCollectionSetIdOrderBySortOrderAsc(Long collectionSetId);

	Optional<CollectionCard> findByIdAndCollectionSetId(Long id, Long collectionSetId);

	Optional<CollectionCard> findByExpansionIdAndBlueprintId(long expansionId, long blueprintId);
}
