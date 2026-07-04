package com.example.cardmonitoring.monitoring;

public record CreatedMonitoringResponse(
		MonitoringResponse monitoring,
		PriceObservationResponse initialObservation) {
}
