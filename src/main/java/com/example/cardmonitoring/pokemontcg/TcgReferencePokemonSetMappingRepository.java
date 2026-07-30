package com.example.cardmonitoring.pokemontcg;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TcgReferencePokemonSetMappingRepository
		extends JpaRepository<TcgReferencePokemonSetMapping, Long> {
}
