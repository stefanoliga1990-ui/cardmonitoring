package com.example.cardmonitoring.monitoring;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitoringRepository extends JpaRepository<Monitoring, Long> {

	List<Monitoring> findByActiveTrue();

	List<Monitoring> findByActiveTrueOrderByIdAsc();

	List<Monitoring> findByOwnerIdAndActiveTrueOrderByCreatedAtDesc(long ownerId);

	List<Monitoring> findByOwnerIdAndActiveTrueAndExpansionId(long ownerId, long expansionId);

	Optional<Monitoring> findByIdAndOwnerId(long id, long ownerId);

	long deleteByOwnerId(long ownerId);
}
