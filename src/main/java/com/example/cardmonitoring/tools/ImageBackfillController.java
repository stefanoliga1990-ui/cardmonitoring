package com.example.cardmonitoring.tools;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.core.Authentication;

import com.example.cardmonitoring.security.AdminAccessService;

@RestController
@RequestMapping("/api/tools/image-backfill")
public class ImageBackfillController {

	private final ImageBackfillService imageBackfillService;
	private final AdminAccessService adminAccessService;

	public ImageBackfillController(ImageBackfillService imageBackfillService, AdminAccessService adminAccessService) {
		this.imageBackfillService = imageBackfillService;
		this.adminAccessService = adminAccessService;
	}

	@GetMapping("/status")
	public ImageBackfillStatusResponse status(Authentication authentication) {
		adminAccessService.requireAdministrator(authentication);
		return imageBackfillService.status();
	}

	@PostMapping("/start")
	public ImageBackfillStatusResponse start(Authentication authentication) {
		adminAccessService.requireAdministrator(authentication);
		return imageBackfillService.start();
	}

	@PostMapping("/stop")
	public ImageBackfillStatusResponse stop(Authentication authentication) {
		adminAccessService.requireAdministrator(authentication);
		return imageBackfillService.stop();
	}
}
