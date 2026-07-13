package com.example.cardmonitoring.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;

import com.example.cardmonitoring.cardtrader.CardTraderException;
import com.example.cardmonitoring.catalog.CatalogNotFoundException;
import com.example.cardmonitoring.collection.CollectionNotFoundException;
import com.example.cardmonitoring.monitoring.MonitoringInactiveException;
import com.example.cardmonitoring.monitoring.MonitoringNotFoundException;
import com.example.cardmonitoring.monitoring.MonitoringRefreshInProgressException;
import com.example.cardmonitoring.user.UsernameAlreadyExistsException;

@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(CardTraderException.class)
	ResponseEntity<ProblemDetail> handleCardTraderException(CardTraderException exception) {
		HttpStatus status = switch (exception.getReason()) {
			case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
			case CONFIGURATION, RATE_LIMIT, REMOTE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
			case AUTHENTICATION, HTTP_ERROR, INVALID_RESPONSE -> HttpStatus.BAD_GATEWAY;
		};

		return problem(status, "CardTrader integration error", exception.getReason().name(), exception.getMessage());
	}

	@ExceptionHandler(CatalogNotFoundException.class)
	ResponseEntity<ProblemDetail> handleCatalogNotFoundException(CatalogNotFoundException exception) {
		return problem(HttpStatus.NOT_FOUND, "Catalog item not found", "CATALOG_NOT_FOUND",
				exception.getMessage());
	}

	@ExceptionHandler(CollectionNotFoundException.class)
	ResponseEntity<ProblemDetail> handleCollectionNotFoundException(CollectionNotFoundException exception) {
		return problem(HttpStatus.NOT_FOUND, "Collection not found", "COLLECTION_NOT_FOUND",
				exception.getMessage());
	}

	@ExceptionHandler(MonitoringNotFoundException.class)
	ResponseEntity<ProblemDetail> handleMonitoringNotFoundException(MonitoringNotFoundException exception) {
		return problem(HttpStatus.NOT_FOUND, "Monitoring not found", "MONITORING_NOT_FOUND",
				exception.getMessage());
	}

	@ExceptionHandler(MonitoringInactiveException.class)
	ResponseEntity<ProblemDetail> handleMonitoringInactiveException(MonitoringInactiveException exception) {
		return problem(HttpStatus.CONFLICT, "Monitoring is inactive", "MONITORING_INACTIVE",
				exception.getMessage());
	}

	@ExceptionHandler(MonitoringRefreshInProgressException.class)
	ResponseEntity<ProblemDetail> handleMonitoringRefreshInProgressException(
			MonitoringRefreshInProgressException exception) {
		return problem(HttpStatus.CONFLICT, "Monitoring refresh in progress", "MONITORING_REFRESH_IN_PROGRESS",
				exception.getMessage());
	}

	@ExceptionHandler(UsernameAlreadyExistsException.class)
	ResponseEntity<ProblemDetail> handleUsernameAlreadyExistsException(UsernameAlreadyExistsException exception) {
		return problem(HttpStatus.CONFLICT, "Username unavailable", "USERNAME_ALREADY_EXISTS",
				exception.getMessage());
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ProblemDetail> handleAuthenticationException() {
		return problem(HttpStatus.UNAUTHORIZED, "Authentication failed", "INVALID_CREDENTIALS",
				"Invalid username or password");
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ProblemDetail> handleIllegalArgumentException(IllegalArgumentException exception) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid request", "INVALID_REQUEST", exception.getMessage());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ProblemDetail> handleUnreadableRequest() {
		return problem(HttpStatus.BAD_REQUEST, "Invalid request", "INVALID_REQUEST",
				"Request body is missing or malformed");
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ProblemDetail> handleTypeMismatch() {
		return problem(HttpStatus.BAD_REQUEST, "Invalid request", "INVALID_REQUEST",
				"Request parameter has an invalid type");
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ProblemDetail> handleNoResourceFoundException() {
		return problem(HttpStatus.NOT_FOUND, "Resource not found", "RESOURCE_NOT_FOUND",
				"The requested resource does not exist");
	}

	@ExceptionHandler(ErrorResponseException.class)
	ResponseEntity<ProblemDetail> handleSpringWebError(ErrorResponseException exception) {
		HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
		String detail = exception.getBody().getDetail();
		return problem(status, "Request error", "REQUEST_ERROR",
				detail == null ? status.getReasonPhrase() : detail);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> handleUnexpectedException(Exception exception) {
		LOGGER.error("Unexpected REST request failure", exception);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "INTERNAL_ERROR",
				"An unexpected error occurred");
	}

	private static ResponseEntity<ProblemDetail> problem(
			HttpStatus status, String title, String code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setProperty("code", code);
		return ResponseEntity.status(status).body(problem);
	}
}
