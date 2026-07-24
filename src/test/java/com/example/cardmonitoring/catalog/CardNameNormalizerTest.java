package com.example.cardmonitoring.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CardNameNormalizerTest {

	@Test
	void removesOnlyTrailingNumericLevels() {
		assertThat(CardNameNormalizer.withoutTrailingNumericLevel("Dialga Lv.68")).isEqualTo("Dialga");
		assertThat(CardNameNormalizer.withoutTrailingNumericLevel("Misty's Tentacool Lv.12")).isEqualTo("Misty's Tentacool");
		assertThat(CardNameNormalizer.withoutTrailingNumericLevel("Garchomp LV.X")).isEqualTo("Garchomp LV.X");
		assertThat(CardNameNormalizer.withoutTrailingNumericLevel("Brock's Rhydon")).isEqualTo("Brock's Rhydon");
	}
}
