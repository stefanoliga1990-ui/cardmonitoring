package com.example.cardmonitoring.collection;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cardmonitoring.user.AppUserPrincipal;

@RestController
@RequestMapping("/api/collections")
public class CollectionController {

	private final CollectionService collectionService;

	public CollectionController(CollectionService collectionService) {
		this.collectionService = collectionService;
	}

	@GetMapping
	public List<CollectionSummaryResponse> findActive(Authentication authentication) {
		return collectionService.findActive(userId(authentication));
	}

	@PostMapping
	public ResponseEntity<CollectionDetailResponse> create(
			Authentication authentication,
			@RequestBody CreateCollectionRequest request) {
		CollectionDetailResponse created = collectionService.create(userId(authentication), request);
		return ResponseEntity
				.created(URI.create("/api/collections/" + created.id()))
				.body(created);
	}

	@GetMapping("/{id}")
	public CollectionDetailResponse findById(Authentication authentication, @PathVariable long id) {
		return collectionService.findById(userId(authentication), id);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(Authentication authentication, @PathVariable long id) {
		collectionService.delete(userId(authentication), id);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/{id}/cards/{cardId}/owned")
	public CollectionDetailResponse updateCardOwnership(
			Authentication authentication,
			@PathVariable long id,
			@PathVariable long cardId,
			@RequestBody UpdateCollectionCardRequest request) {
		return collectionService.updateCardOwnership(userId(authentication), id, cardId, request);
	}

	private static long userId(Authentication authentication) {
		if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
			throw new IllegalStateException("Authenticated user is unavailable");
		}
		return principal.userId();
	}
}
