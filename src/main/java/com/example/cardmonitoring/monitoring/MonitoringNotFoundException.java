package com.example.cardmonitoring.monitoring;

public class MonitoringNotFoundException extends RuntimeException {

	public MonitoringNotFoundException(long monitoringId) {
		super("Monitoring not found: " + monitoringId);
	}
}
