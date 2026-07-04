package com.example.cardmonitoring.monitoring;

public class MonitoringRefreshInProgressException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public MonitoringRefreshInProgressException(long monitoringId) {
		super("A refresh is already in progress for monitoring " + monitoringId);
	}
}
