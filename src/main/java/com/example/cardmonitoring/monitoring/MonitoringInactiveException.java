package com.example.cardmonitoring.monitoring;

public class MonitoringInactiveException extends RuntimeException {

	public MonitoringInactiveException(long monitoringId) {
		super("Monitoring is inactive: " + monitoringId);
	}
}
