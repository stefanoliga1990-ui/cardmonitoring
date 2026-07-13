package com.example.cardmonitoring.pokemontcg;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CardImageRepository extends JpaRepository<StoredCardImage, Long> {

	Optional<StoredCardImage> findByExpansionIdAndBlueprintIdAndCollectorNumberAndImageSource(
			long expansionId,
			long blueprintId,
			String collectorNumber,
			String imageSource);

	List<StoredCardImage> findByExpansionIdAndImageSource(long expansionId, String imageSource);
}
