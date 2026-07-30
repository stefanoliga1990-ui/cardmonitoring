package com.example.cardmonitoring.tools;

import org.springframework.security.core.Authentication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cardmonitoring.security.AdminAccessService;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

	private final AdminAccessService adminAccessService;
	private final ImageCoverageAuditService imageCoverageAuditService;

	public AdminController(AdminAccessService adminAccessService, ImageCoverageAuditService imageCoverageAuditService) {
		this.adminAccessService = adminAccessService;
		this.imageCoverageAuditService = imageCoverageAuditService;
	}

	@GetMapping("/status")
	public AdminStatusResponse status(Authentication authentication) {
		adminAccessService.requireAdministrator(authentication);
		return new AdminStatusResponse(true);
	}

	@GetMapping("/image-coverage/status")
	public ImageCoverageAuditStatusResponse imageCoverageStatus(Authentication authentication) {
		adminAccessService.requireAdministrator(authentication);
		return imageCoverageAuditService.status();
	}

	@PostMapping("/image-coverage/start")
	public ImageCoverageAuditStatusResponse startImageCoverageAudit(Authentication authentication) {
		adminAccessService.requireAdministrator(authentication);
		return imageCoverageAuditService.start();
	}

	@GetMapping(value = "/image-coverage/export", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ImageCoverageExportResponse> exportImageCoverage(Authentication authentication) {
		adminAccessService.requireAdministrator(authentication);
		try {
			return ResponseEntity.ok()
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=card-images-missing.json")
					.body(imageCoverageAuditService.export());
		}
		catch (IllegalStateException exception) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
	}
}
