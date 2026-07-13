package com.example.cardmonitoring.collection;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCollectionCardRepository extends JpaRepository<UserCollectionCard, Long> {

	List<UserCollectionCard> findByUserCollectionId(Long userCollectionId);

	Optional<UserCollectionCard> findByUserCollectionIdAndCollectionCardId(Long userCollectionId, Long collectionCardId);
}
