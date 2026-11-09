package com.wunderlore.game.whosaidit;

import java.util.List;
import java.util.Set;

import com.wunderlore.lore.LoreCharacter;
import com.wunderlore.lore.LoreData;
import com.wunderlore.lore.LoreQuote;

// Upstream records both Who Said It? modes must never use. They are matched by exact id only, never
// by name, prefix, dialog or length. The lore data and the public /quotes catalog are left as they
// are: this applies only to what the two game modes draw from LoreData.
final class WhoSaidItExclusions {

    // Records that are not individual characters: two wiki user pages, both MINOR_CHARACTER
    // entries (the second owns 117 quotes), Seers, the "Letter 211" document label and the
    // generic "Men" category. They are never a correct answer, an option or a speaker.
    static final Set<String> CHARACTER_IDS = Set.of(
            "5cd99d4bde30eff6ebccfc96",  // User:Deathshriek
            "5cd99d4bde30eff6ebccfe10",  // User:Technobliterator/Showcase
            "5cdbe47d7ed9587226e7949f",  // MINOR_CHARACTER
            "5cdbe49b7ed9587226e794a0",  // MINOR_CHARACTER (generic)
            "5cdbdecb6dc0baeae48cfa43",  // Seers
            "5cdbdecb6dc0baeae48cfac2",  // Letter 211
            "5cdbdecb6dc0baeae48cfa8d"); // Men

    // Quotes with confirmed upstream problems: the Barrow-wight's song filed under Gollum, a
    // corrupted line filed under Aragorn and the password line cut off at a dangling "(". Only these quotes go; the characters stay.
    static final Set<String> QUOTE_IDS = Set.of(
            "5cd96e05de30eff6ebccedde",
            "5cd96e05de30eff6ebccec49",
            "5cd96e05de30eff6ebcceead");

    private WhoSaidItExclusions() {
    }

    // Characters either mode may offer or accept as the speaker.
    static List<LoreCharacter> playableCharacters(LoreData data) {
        return data.charactersById().values().stream()
                .filter(character -> !CHARACTER_IDS.contains(character.id()))
                .toList();
    }

    // Attributed quotes either mode may use: not an excluded quote, and not by an excluded character.
    static List<LoreQuote> playableQuotes(LoreData data) {
        return data.attributedQuotes().stream()
                .filter(quote -> !QUOTE_IDS.contains(quote.id()))
                .filter(quote -> !CHARACTER_IDS.contains(quote.characterId()))
                .toList();
    }
}
