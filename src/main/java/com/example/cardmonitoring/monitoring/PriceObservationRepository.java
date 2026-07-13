package com.example.cardmonitoring.monitoring;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PriceObservationRepository extends JpaRepository<PriceObservation, Long> {

	List<PriceObservation> findByMonitoringIdOrderByObservedAtAsc(Long monitoringId);

	@Query("""
			select observation
			from PriceObservation observation
			where observation.monitoring.id in :monitoringIds
			order by observation.monitoring.id asc, observation.observedAt asc
			""")
	List<PriceObservation> findByMonitoringIdsOrderByMonitoringIdAndObservedAt(
			@Param("monitoringIds") List<Long> monitoringIds);
}
