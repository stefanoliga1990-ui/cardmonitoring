package com.example.cardmonitoring.monitoring;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceObservationRepository extends JpaRepository<PriceObservation, Long> {

	List<PriceObservation> findByMonitoringIdOrderByObservedAtAsc(Long monitoringId);
}
