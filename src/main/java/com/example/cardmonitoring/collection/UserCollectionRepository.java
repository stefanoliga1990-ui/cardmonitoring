package com.example.cardmonitoring.collection;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCollectionRepository extends JpaRepository<UserCollection, Long> {

	List<UserCollection> findByOwnerIdAndActiveTrueOrderByCreatedAtDesc(long ownerId);

	Optional<UserCollection> findByIdAndOwnerIdAndActiveTrue(long id, long ownerId);

	Optional<UserCollection> findByOwnerIdAndCollectionSetId(long ownerId, Long collectionSetId);

	long deleteByOwnerId(long ownerId);
}
