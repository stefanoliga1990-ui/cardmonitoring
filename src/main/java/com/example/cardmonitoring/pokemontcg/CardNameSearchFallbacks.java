package com.example.cardmonitoring.pokemontcg;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

/**
 * Generates conservative alternate spellings used only after an exact card-name lookup fails.
 * The source CardTrader name is never modified for display or persistence.
 */
final class CardNameSearchFallbacks {

	private static final int MAXIMUM_VARIANTS = 12;
	private static final Pattern SEPARATOR_SUFFIX = Pattern.compile("\\s+-\\s+.+$");
	private static final Pattern TRAILING_DECORATIVE_SYMBOL = Pattern.compile("\\s*[☆★◇◆]\\s*$");
	private static final Pattern TRAILING_GREEK_VARIANT = Pattern.compile(
			"(?iu)\\s+(?:(?:[αβγδ]\\s*)?(?:alpha|beta|gamma|delta)(?:\\s+species)?|δ\\s+delta\\s+species)\\s*$");
	private static final Pattern TRAILING_EX_OR_GX = Pattern.compile("(?iu)\\s+(ex|gx)\\s*$");
	private static final Pattern TRAILING_FOUR = Pattern.compile("\\s+4\\s*$");
	private static final Pattern ENERGY_SYMBOL = Pattern.compile("(?i)\\[([WRGLPFDMYC])\\]");
	private static final Map<String, String> ENERGY_SYMBOL_NAMES = Map.of(
			"W", "Water", "R", "Fire", "G", "Grass", "L", "Lightning", "P", "Psychic",
			"F", "Fighting", "D", "Darkness", "M", "Metal", "Y", "Fairy", "C", "Colorless");

	private CardNameSearchFallbacks() {
	}

	/** Returns alternate query names, excluding the original name and preserving deterministic order. */
	static List<String> alternatives(String cardName) {
		if (!StringUtils.hasText(cardName)) return List.of();
		String original = cardName.trim();
		Set<String> seen = new LinkedHashSet<>();
		Queue<String> pending = new ArrayDeque<>();
		seen.add(original);
		pending.add(original);

		while (!pending.isEmpty() && seen.size() <= MAXIMUM_VARIANTS) {
			String current = pending.remove();
			for (String alternative : immediateAlternatives(current)) {
				if (StringUtils.hasText(alternative) && seen.add(alternative)) {
					pending.add(alternative);
				}
			}
		}
		seen.remove(original);
		return List.copyOf(seen);
	}

	private static List<String> immediateAlternatives(String value) {
		LinkedHashSet<String> alternatives = new LinkedHashSet<>();
		addIfChanged(alternatives, value, SEPARATOR_SUFFIX.matcher(value).replaceFirst(""));
		addIfChanged(alternatives, value, removeSuffixIgnoreCase(value, " Prism Star"));
		addIfChanged(alternatives, value, removeSuffixIgnoreCase(value, " Gold Star"));
		addIfChanged(alternatives, value, removeSuffixIgnoreCase(value, " Star"));
		addIfChanged(alternatives, value, TRAILING_DECORATIVE_SYMBOL.matcher(value).replaceFirst(""));
		addIfChanged(alternatives, value, TRAILING_GREEK_VARIANT.matcher(value).replaceFirst(""));

		Matcher exOrGx = TRAILING_EX_OR_GX.matcher(value);
		if (exOrGx.find()) {
			addIfChanged(alternatives, value, exOrGx.replaceFirst("-" + exOrGx.group(1).toUpperCase(Locale.ROOT)));
		}
		addIfChanged(alternatives, value, expandEnergySymbols(value));
		addIfChanged(alternatives, value, TRAILING_FOUR.matcher(value).replaceFirst(" E4"));
		return List.copyOf(alternatives);
	}

	private static void addIfChanged(Set<String> values, String original, String candidate) {
		if (candidate != null && !candidate.isBlank() && !candidate.equals(original)) {
			values.add(candidate.trim().replaceAll("\\s+", " "));
		}
	}

	private static String removeSuffixIgnoreCase(String value, String suffix) {
		return value.regionMatches(true, value.length() - suffix.length(), suffix, 0, suffix.length())
				? value.substring(0, value.length() - suffix.length()).trim()
				: value;
	}

	private static String expandEnergySymbols(String value) {
		Matcher matcher = ENERGY_SYMBOL.matcher(value);
		StringBuffer expanded = new StringBuffer();
		while (matcher.find()) {
			String replacement = ENERGY_SYMBOL_NAMES.get(matcher.group(1).toUpperCase(Locale.ROOT));
			matcher.appendReplacement(expanded, replacement == null ? matcher.group() : replacement);
		}
		matcher.appendTail(expanded);
		return expanded.toString();
	}
}
