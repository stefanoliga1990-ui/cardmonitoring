package com.example.cardmonitoring.cardtrader;

public final class CardTraderException extends RuntimeException {

	public enum Reason {
		CONFIGURATION,
		AUTHENTICATION,
		RATE_LIMIT,
		TIMEOUT,
		HTTP_ERROR,
		REMOTE_UNAVAILABLE,
		INVALID_RESPONSE
	}

	private final Reason reason;
	private final Integer httpStatus;

	private CardTraderException(Reason reason, String message, Integer httpStatus, Throwable cause) {
		super(message, cause);
		this.reason = reason;
		this.httpStatus = httpStatus;
	}

	static CardTraderException configuration(String message) {
		return new CardTraderException(Reason.CONFIGURATION, message, null, null);
	}

	static CardTraderException forHttpStatus(int status) {
		if (status == 401 || status == 403) {
			return new CardTraderException(Reason.AUTHENTICATION,
					"CardTrader authentication failed", status, null);
		}
		if (status == 429) {
			return new CardTraderException(Reason.RATE_LIMIT,
					"CardTrader rate limit exceeded", status, null);
		}
		if (status >= 500) {
			return new CardTraderException(Reason.REMOTE_UNAVAILABLE,
					"CardTrader service is unavailable", status, null);
		}
		return new CardTraderException(Reason.HTTP_ERROR,
				"CardTrader rejected the request", status, null);
	}

	static CardTraderException timeout(Throwable cause) {
		return new CardTraderException(Reason.TIMEOUT, "CardTrader request timed out", null, cause);
	}

	static CardTraderException unavailable(Throwable cause) {
		return new CardTraderException(Reason.REMOTE_UNAVAILABLE,
				"Unable to communicate with CardTrader", null, cause);
	}

	public static CardTraderException invalidResponse(Throwable cause) {
		return new CardTraderException(Reason.INVALID_RESPONSE,
				"CardTrader returned an invalid response", null, cause);
	}

	public Reason getReason() {
		return reason;
	}

	public Integer getHttpStatus() {
		return httpStatus;
	}
}
