package com.example.cardmonitoring.pokemontcg;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TcgCollectorReferenceCatalogServiceTest {

	@Test
	void normalizesStandardAndPrefixedReferenceNumbersWithoutTreatingTextAsANumber() {
		assertThat(TcgCollectorReferenceCatalogService.normalizeCollectorNumber("001/165")).isEqualTo("1");
		assertThat(TcgCollectorReferenceCatalogService.normalizeCollectorNumber("H01/H32")).isEqualTo("H1");
		assertThat(TcgCollectorReferenceCatalogService.normalizeCollectorNumber("TG01/TG30")).isEqualTo("TG1");
		assertThat(TcgCollectorReferenceCatalogService.normalizeCollectorNumber("Hidden Fates")).isBlank();
	}

	@Test
	void normalizesHtmlAmpersandsInReferenceSetNames() {
		assertThat(TcgCollectorReferenceCatalogService.normalizeSetName("Black &amp; White"))
				.isEqualTo(TcgCollectorReferenceCatalogService.normalizeSetName("Black & White"));
	}
}
