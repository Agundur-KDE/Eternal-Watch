// SPDX-License-Identifier: GPL-3.0-only
// Catalog adapted from EL-Klock Days.js; English game names are not UI translations.
package de.agundur.eternalwatch.core;
import java.util.*;
public final class SpecialDays {
    private SpecialDays() { }
    public static final List<String> NAMES = Collections.unmodifiableList(Arrays.asList(
        "Acid Rain Day",
        "Day of Alchemy",
        "Day of Aleksei Stakhanov",
        "Day of Alfred Nobel",
        "Day of Armor",
        "Day of Bones",
        "Day of Evanescence",
        "Day of Faster Respawns",
        "Day of Fasting",
        "Day of half Cooldowns",
        "Day of Invasions",
        "Day of Joule",
        "Day of Magic",
        "Day of Mana",
        "Day of Manufacturing",
        "Day of More Grief",
        "Day of Murphy",
        "Day of Mushrooms",
        "Day of No Grief",
        "Day of No Rostogols",
        "Day of Positivity",
        "Day of Recycling",
        "Day of Robin Tell",
        "Day of Running",
        "Day of Schools",
        "Day of Stronger Animals and Monsters",
        "Day of Summoners",
        "Day of Sun Tzu",
        "Day of Tailoring",
        "Day of the Brave",
        "Day of the Dead",
        "Day of the Horses",
        "Day of the Print",
        "Decreased Harvesting Events Day",
        "Decreased Rare Manufacturing day",
        "Green Day",
        "Healing Day",
        "Increased Harvesting Events Day",
        "Increased rare manufacturing day",
        "Labour Day",
        "Leonard's Day",
        "No Drops Day",
        "Non Stop Day",
        "Peace Day",
        "Reconstruction Day",
        "Scholars Day",
        "Scientists Day",
        "Scotty's Vacation",
        "Solar Flares Day"));
    public static String key(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace('’', '\'').replaceAll("\\s+", " ");
    }
    public static boolean normal(String name) {
        return key(name).matches("normal day|ordinary day|today is an ordinary day:?");
    }
    public static String resolve(String name,String description) {
        String candidate=name.trim();
        if (key(candidate).matches("today is a special day:?|special day:?")) candidate=description.trim();
        for (String known:NAMES) if (key(known).equals(key(candidate))) return known;
        return candidate;
    }
    public static boolean selected(String name,Set<String> names) {
        for (String candidate:names) if (key(candidate).equals(key(name))) return true;
        return false;
    }
}
