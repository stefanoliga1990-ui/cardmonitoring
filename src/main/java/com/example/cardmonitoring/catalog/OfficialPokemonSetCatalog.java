package com.example.cardmonitoring.catalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;

import com.example.cardmonitoring.cardtrader.CardTraderExpansion;

/**
 * Curated Pokémon set catalogue based on the supplied TCG Collector list.
 * CardTrader occasionally uses another name for the same official set, so a
 * small, explicit list of verified CardTrader codes is kept alongside names.
 */
public final class OfficialPokemonSetCatalog {

	private static final String RESOURCE_PATH = "catalog/tcg-collector-official-set-names.txt";
	private static final int EXPECTED_CURATED_SET_COUNT = 336;
	private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+", Pattern.CASE_INSENSITIVE);
	private static final Set<String> OFFICIAL_SET_NAMES = loadOfficialSetNames();
	private static final Set<String> CARDTRADER_CODE_ALIASES = Set.of(
			"ar", "ba-20", "ba-22", "ba-2024", "bisharp", "bwbsp", "clb", "clc", "clv", "dpbsp", "futsal",
			"gyarados", "hggsbs", "holy", "kss", "m24", "mep", "mew", "mc21", "my1battle", "nbsp",
			"pccp", "pkmgo", "playprizep", "pr19", "raichu", "rc", "rclt", "shbs", "smbs", "swshbs", "tk1", "tk10", "tk11",
			"trickortrade", "suicune", "wigglytuff", "wiz", "xytka", "xytkas", "xytkl", "xytkn", "xytkos",
			"xytkp", "xyths", "wcd2004", "wcd2005", "wcd2006", "wcd2007", "wcd2008", "wcd2009",
			"wcd2010", "wcd2011", "wcd2012", "wcd2013", "wcd2014", "wcd2015", "wcd2016", "wcd2017",
			"wcd2018", "wcd2019", "wcd2022", "wcd2023", "wcd2024", "wcd2025");
	/** Curated exclusions for deck products, special promos and World Championship decks. */
	private static final Set<String> EXCLUDED_CARDTRADER_EXPANSION_CODES = Set.of(
			"ba-20", "nbsp", "playprizep", "clb", "clc", "clv", "swshbs",
			"wcd2004", "wcd2005", "wcd2006", "wcd2007", "wcd2008", "wcd2009",
			"wcd2010", "wcd2011", "wcd2012", "wcd2013", "wcd2014", "wcd2015",
			"wcd2016", "wcd2017", "wcd2018", "wcd2019", "wcd2022", "wcd2023");
	/**
	 * CardTrader expansions that are present in its catalogue but currently do
	 * not contain any Pokemon Singles blueprints. They are intentionally not
	 * offered as collections or monitoring candidates.
	 */
	private static final Set<String> EMPTY_CARDTRADER_EXPANSION_CODES = Set.of("30c");

	private OfficialPokemonSetCatalog() {
	}

	public static boolean includes(CardTraderExpansion expansion) {
		return expansion != null && includes(expansion.name(), expansion.code());
	}

	public static boolean includes(String expansionName, String expansionCode) {
		return !isExcludedCuratedSet(expansionName, expansionCode)
				&& !EMPTY_CARDTRADER_EXPANSION_CODES.contains(normalizeCode(expansionCode))
				&& (OFFICIAL_SET_NAMES.contains(normalize(expansionName))
				|| CARDTRADER_CODE_ALIASES.contains(normalizeCode(expansionCode)));
	}

	private static boolean isExcludedCuratedSet(String expansionName, String expansionCode) {
		String normalizedName = normalize(expansionName);
		return normalizedName.contains("trainer kit")
				|| EXCLUDED_CARDTRADER_EXPANSION_CODES.contains(normalizeCode(expansionCode))
				|| normalizedName.equals("battle academy 2020")
				|| normalizedName.equals("mcdonald s collection 2018 french")
				|| normalizedName.equals("nintendo black star promos")
				|| normalizedName.equals("nintendo promos")
				|| normalizedName.equals("play pokemon prize pack series")
				|| normalizedName.equals("pokemon tcg classic blastoise suicune ex deck")
				|| normalizedName.equals("pokemon tcg classic charizard ho oh ex deck")
				|| normalizedName.equals("pokemon tcg classic venusaur lugia ex deck")
				|| normalizedName.equals("swsh black star promos")
				|| normalizedName.matches("world championship decks (200[4-9]|201[0-9]|202[2-3])");
	}

	static String normalize(String value) {
		if (value == null) {
			return "";
		}
		String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
		String withoutDiacritics = decomposed.replaceAll("\\p{M}+", "");
		return NON_ALPHANUMERIC.matcher(withoutDiacritics.toLowerCase(Locale.ROOT)).replaceAll(" ").trim()
				.replaceAll("\\s+", " ");
	}

	private static String normalizeCode(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private static Set<String> loadOfficialSetNames() {
		try {
			ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
			List<String> lines = resource.getContentAsString(StandardCharsets.UTF_8).lines().toList();
			Set<String> names = new LinkedHashSet<>();
			for (String line : lines) {
				String normalized = normalize(line);
				if (!normalized.isBlank()) {
					names.add(normalized);
				}
			}
			if (names.size() != EXPECTED_CURATED_SET_COUNT) {
				throw new IllegalStateException("Expected 358 official Pokémon set names but found " + names.size());
			}
			return Set.copyOf(names);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Unable to load curated Pokémon set catalogue", exception);
		}
	}
}
