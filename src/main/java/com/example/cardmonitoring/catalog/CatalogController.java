package com.example.cardmonitoring.catalog;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

	private final CatalogService catalogService;

	public CatalogController(CatalogService catalogService) {
		this.catalogService = catalogService;
	}

	@GetMapping("/expansions")
	public List<CatalogExpansion> getExpansions() {
		return catalogService.getPokemonExpansions();
	}

	@GetMapping("/expansions/{id}/blueprints")
	public List<CatalogBlueprint> getBlueprints(@PathVariable long id) {
		return catalogService.getPokemonBlueprints(id);
	}
}
