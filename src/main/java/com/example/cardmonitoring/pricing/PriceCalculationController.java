package com.example.cardmonitoring.pricing;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/price-calculations")
public class PriceCalculationController {

	private final PriceCalculationPreviewService previewService;

	public PriceCalculationController(PriceCalculationPreviewService previewService) {
		this.previewService = previewService;
	}

	@PostMapping
	public PriceCalculationPreviewResponse calculate(@RequestBody PriceCalculationRequest request) {
		return previewService.calculate(request);
	}
}
