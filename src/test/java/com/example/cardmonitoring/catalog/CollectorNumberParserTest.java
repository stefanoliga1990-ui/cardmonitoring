package com.example.cardmonitoring.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CollectorNumberParserTest {

	@Test
	void extractsCollectorNumberFromTheNumberSegment() {
		assertThat(CollectorNumberParser.fromVersion("Holo Rare | 004/102")).contains("004");
		assertThat(CollectorNumberParser.fromVersion("Shadowless | Holo Rare 1/102")).contains("1");
	}

	@Test
	void doesNotTreatYearOrStampAsCollectorNumber() {
		assertThat(CollectorNumberParser.fromVersion("2017 | Charizard Stamp 2")).isEmpty();
	}
}
