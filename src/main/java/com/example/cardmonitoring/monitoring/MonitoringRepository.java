package com.example.cardmonitoring.monitoring;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitoringRepository extends JpaRepository<Monitoring, Long> {

	List<Monitoring> findByActiveTrue();

	List<Monitoring> findByActiveTrueOrderByIdAsc();

	List<Monitoring> findByOwnerIdAndActiveTrueOrderByCreatedAtDesc(long ownerId);

	Optional<Monitoring> findByIdAndOwnerId(long id, long ownerId);
}
