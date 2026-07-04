package com.example.cardmonitoring.monitoring;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import com.example.cardmonitoring.user.AppUserPrincipal;

@RestController
@RequestMapping("/api/monitorings")
public class MonitoringController {

	private final MonitoringService monitoringService;

	public MonitoringController(MonitoringService monitoringService) {
		this.monitoringService = monitoringService;
	}

	@PostMapping
	public ResponseEntity<CreatedMonitoringResponse> create(
			Authentication authentication,
			@RequestBody CreateMonitoringRequest request) {
		CreatedMonitoringResponse created = monitoringService.create(userId(authentication), request);
		URI location = URI.create("/api/monitorings/" + created.monitoring().id());
		return ResponseEntity.created(location).body(created);
	}

	@GetMapping
	public List<MonitoringResponse> findActive(Authentication authentication) {
		return monitoringService.findActive(userId(authentication));
	}

	@GetMapping("/{id}")
	public MonitoringResponse findById(Authentication authentication, @PathVariable long id) {
		return monitoringService.findById(userId(authentication), id);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deactivate(Authentication authentication, @PathVariable long id) {
		monitoringService.deactivate(userId(authentication), id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/refresh")
	public PriceObservationResponse refresh(Authentication authentication, @PathVariable long id) {
		return monitoringService.refresh(userId(authentication), id);
	}

	@GetMapping("/{id}/observations")
	public List<PriceObservationResponse> findObservations(Authentication authentication, @PathVariable long id) {
		return monitoringService.findObservations(userId(authentication), id);
	}

	private static long userId(Authentication authentication) {
		if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
			throw new IllegalStateException("Authenticated user is unavailable");
		}
		return principal.userId();
	}
}
