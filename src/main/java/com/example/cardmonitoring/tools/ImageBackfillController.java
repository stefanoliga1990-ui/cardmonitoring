package com.example.cardmonitoring.tools;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tools/image-backfill")
public class ImageBackfillController {

	private final ImageBackfillService imageBackfillService;

	public ImageBackfillController(ImageBackfillService imageBackfillService) {
		this.imageBackfillService = imageBackfillService;
	}

	@GetMapping("/status")
	public ImageBackfillStatusResponse status() {
		return imageBackfillService.status();
	}

	@PostMapping("/start")
	public ImageBackfillStatusResponse start() {
		return imageBackfillService.start();
	}

	@PostMapping("/stop")
	public ImageBackfillStatusResponse stop() {
		return imageBackfillService.stop();
	}
}
