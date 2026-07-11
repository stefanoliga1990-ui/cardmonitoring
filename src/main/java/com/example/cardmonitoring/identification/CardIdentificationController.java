package com.example.cardmonitoring.identification;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/card-identification")
public class CardIdentificationController {

	private final CardIdentificationService cardIdentificationService;

	public CardIdentificationController(CardIdentificationService cardIdentificationService) {
		this.cardIdentificationService = cardIdentificationService;
	}

	@PostMapping("/candidates")
	public List<CardIdentificationCandidate> findCandidates(@RequestBody CardIdentificationRequest request) {
		return cardIdentificationService.findCandidates(request);
	}
}
