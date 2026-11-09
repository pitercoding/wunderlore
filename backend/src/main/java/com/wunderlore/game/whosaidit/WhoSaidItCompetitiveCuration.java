package com.wunderlore.game.whosaidit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.wunderlore.lore.LoreQuote;

// The extra editorial curation of the competitive Who Said It? mode. It runs on top of
// WhoSaidItExclusions.playableQuotes, so it only ever sees quotes of characters both modes may
// use. The single-round game and the public /quotes catalog do not call it.
//
// The rules come from the editorial audit of the upstream data and run in this order; every
// quote is removed by the first rule that applies, so nothing is counted twice:
//   1. The same text attributed to two or more different characters: all of its quotes go,
//      because the answer would not be unique.
//   2. The same text repeated by one character: only the quote with the lowest id stays.
//   3. Fewer than three words.
//   4. A clear editorial defect, by exact id (EDITORIAL_QUOTE_IDS). Quotes are never repaired.
//   5. An attribution that is suspected to be wrong, by exact id.
// Quotes in constructed languages are not filtered: they stay unless a rule above removes them.
//
// Texts are compared ignoring case and surrounding or repeated whitespace, but not punctuation:
// "Frodo!" and "FRODO!" are the same text, "Frodo!" and "Frodo?" are not.
final class WhoSaidItCompetitiveCuration {

    static final int MIN_WORDS = 3;

    private static final Pattern WORD = Pattern.compile("[A-Za-z']+");

    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\p{Z}]+");

    // Defects the audit found in the upstream text: glued lines, lost accents, OCR mistakes and
    // stage directions. Matched by exact id only; the text itself is left as received.
    static final Set<String> EDITORIAL_QUOTE_IDS = Set.of(
            // stage direction or unbalanced parenthesis in the dialog (4)
            "5cd96e05de30eff6ebcce908",
            "5cd96e05de30eff6ebccea39",
            "5cd96e05de30eff6ebccec09",
            "5cd96e05de30eff6ebccef29",
            // lowercase "l" where a capital "I" belongs (lt, ls, lf, lsildur) (11)
            "5cd96e05de30eff6ebcce9d2",
            "5cd96e05de30eff6ebccea07",
            "5cd96e05de30eff6ebccea14",
            "5cd96e05de30eff6ebccea1a",
            "5cd96e05de30eff6ebcceae2",
            "5cd96e05de30eff6ebcceae8",
            "5cd96e05de30eff6ebcceae9",
            "5cd96e05de30eff6ebcceb77",
            "5cd96e05de30eff6ebcced29",
            "5cd96e05de30eff6ebcceff4",
            "5cd96e05de30eff6ebccf00b",
            // words fused together (19)
            "5cd96e05de30eff6ebcceb04",
            "5cd96e05de30eff6ebcceb61",
            "5cd96e05de30eff6ebccebb1",
            "5cd96e05de30eff6ebccebf9",
            "5cd96e05de30eff6ebccec2e",
            "5cd96e05de30eff6ebccec7b",
            "5cd96e05de30eff6ebcceceb",
            "5cd96e05de30eff6ebcced1a",
            "5cd96e05de30eff6ebcced4b",
            "5cd96e05de30eff6ebcced50",
            "5cd96e05de30eff6ebcceda0",
            "5cd96e05de30eff6ebcceda1",
            "5cd96e05de30eff6ebcceda3",
            "5cd96e05de30eff6ebccedb0",
            "5cd96e05de30eff6ebccee49",
            "5cd96e05de30eff6ebccee71",
            "5cd96e05de30eff6ebccef0b",
            "5cd96e05de30eff6ebccef90",
            "5cd96e05de30eff6ebccefb4",
            // no space after punctuation (lines glued together) (63)
            "5cd96e05de30eff6ebcce833",
            "5cd96e05de30eff6ebcce948",
            "5cd96e05de30eff6ebccea2a",
            "5cd96e05de30eff6ebcceaca",
            "5cd96e05de30eff6ebcceb4f",
            "5cd96e05de30eff6ebcceb57",
            "5cd96e05de30eff6ebcceb69",
            "5cd96e05de30eff6ebcceb6f",
            "5cd96e05de30eff6ebcceb7c",
            "5cd96e05de30eff6ebccebbf",
            "5cd96e05de30eff6ebccebd7",
            "5cd96e05de30eff6ebccec17",
            "5cd96e05de30eff6ebccec18",
            "5cd96e05de30eff6ebccec2b",
            "5cd96e05de30eff6ebccec35",
            "5cd96e05de30eff6ebccec51",
            "5cd96e05de30eff6ebccec67",
            "5cd96e05de30eff6ebccec80",
            "5cd96e05de30eff6ebccec8c",
            "5cd96e05de30eff6ebccec9a",
            "5cd96e05de30eff6ebccecd8",
            "5cd96e05de30eff6ebccece2",
            "5cd96e05de30eff6ebccecf3",
            "5cd96e05de30eff6ebccecf5",
            "5cd96e05de30eff6ebccecf6",
            "5cd96e05de30eff6ebcced2a",
            "5cd96e05de30eff6ebcced32",
            "5cd96e05de30eff6ebcced43",
            "5cd96e05de30eff6ebcced52",
            "5cd96e05de30eff6ebcced56",
            "5cd96e05de30eff6ebcced6d",
            "5cd96e05de30eff6ebcced72",
            "5cd96e05de30eff6ebcced8a",
            "5cd96e05de30eff6ebcced99",
            "5cd96e05de30eff6ebccedc7",
            "5cd96e05de30eff6ebcceded",
            "5cd96e05de30eff6ebccee0f",
            "5cd96e05de30eff6ebccee4a",
            "5cd96e05de30eff6ebccee63",
            "5cd96e05de30eff6ebccee66",
            "5cd96e05de30eff6ebccee6d",
            "5cd96e05de30eff6ebccee6e",
            "5cd96e05de30eff6ebccee80",
            "5cd96e05de30eff6ebcceebf",
            "5cd96e05de30eff6ebcceed8",
            "5cd96e05de30eff6ebcceedd",
            "5cd96e05de30eff6ebcceeec",
            "5cd96e05de30eff6ebccef00",
            "5cd96e05de30eff6ebccef04",
            "5cd96e05de30eff6ebccef13",
            "5cd96e05de30eff6ebccef30",
            "5cd96e05de30eff6ebccef41",
            "5cd96e05de30eff6ebccef54",
            "5cd96e05de30eff6ebccef57",
            "5cd96e05de30eff6ebccef62",
            "5cd96e05de30eff6ebccef63",
            "5cd96e05de30eff6ebccefd0",
            "5cd96e05de30eff6ebccefed",
            "5cd96e05de30eff6ebccf005",
            "5cd96e05de30eff6ebccf00e",
            "5cd96e05de30eff6ebccf00f",
            "5cd96e05de30eff6ebccf0da",
            "5cd96e05de30eff6ebccf10b",
            // NBSP or stray comma where two lines were glued (18)
            "5cd96e05de30eff6ebcce87b",
            "5cd96e05de30eff6ebcce885",
            "5cd96e05de30eff6ebcce9e4",
            "5cd96e05de30eff6ebcceafc",
            "5cd96e05de30eff6ebcced85",
            "5cd96e05de30eff6ebcced86",
            "5cd96e05de30eff6ebcced94",
            "5cd96e05de30eff6ebccee20",
            "5cd96e05de30eff6ebccee5d",
            "5cd96e05de30eff6ebcceea1",
            "5cd96e05de30eff6ebcceee2",
            "5cd96e05de30eff6ebcceee7",
            "5cd96e05de30eff6ebccef17",
            "5cd96e05de30eff6ebccef21",
            "5cd96e05de30eff6ebccf0a6",
            "5cd96e05de30eff6ebccf0be",
            "5cd96e05de30eff6ebccf0e6",
            "5cd96e05de30eff6ebccf12d",
            // dangling "?," ending (2)
            "5cd96e05de30eff6ebccea13",
            "5cd96e05de30eff6ebcceaa1",
            // accented letter replaced by an apostrophe (17)
            "5cd96e05de30eff6ebcce835",
            "5cd96e05de30eff6ebcce866",
            "5cd96e05de30eff6ebcce8da",
            "5cd96e05de30eff6ebcce8e0",
            "5cd96e05de30eff6ebcce956",
            "5cd96e05de30eff6ebcce997",
            "5cd96e05de30eff6ebcce9bc",
            "5cd96e05de30eff6ebccebdf",
            "5cd96e05de30eff6ebccec9d",
            "5cd96e05de30eff6ebcced1f",
            "5cd96e05de30eff6ebcced62",
            "5cd96e05de30eff6ebccee03",
            "5cd96e05de30eff6ebccee04",
            "5cd96e05de30eff6ebccee74",
            "5cd96e05de30eff6ebccef23",
            "5cd96e05de30eff6ebccf096",
            "5cd96e05de30eff6ebccf130",
            // fragment (5)
            "5cd96e05de30eff6ebcce9cb",
            "5cd96e05de30eff6ebccebea",
            "5cd96e05de30eff6ebcced6c",
            "5cd96e05de30eff6ebcceeda",
            "5cd96e05de30eff6ebcceeef");

    // The line that sits inside a Saruman scene but is attributed to Sauron.
    static final Set<String> SUSPECT_ATTRIBUTION_QUOTE_IDS = Set.of("5cd96e05de30eff6ebccedf3");

    private WhoSaidItCompetitiveCuration() {
    }

    // The quotes the competitive mode may draw from, in their original order.
    static List<LoreQuote> eligibleQuotes(List<LoreQuote> playableQuotes) {
        Map<String, Set<String>> speakersByText = new HashMap<>();

        for (LoreQuote quote : playableQuotes) {
            speakersByText.computeIfAbsent(textKey(quote), key -> new HashSet<>()).add(quote.characterId());
        }

        List<LoreQuote> withoutCollisions = playableQuotes.stream()
                .filter(quote -> speakersByText.get(textKey(quote)).size() == 1)
                .toList();

        Map<String, String> lowestIdPerSpeakerText = new HashMap<>();

        for (LoreQuote quote : withoutCollisions) {
            lowestIdPerSpeakerText.merge(speakerTextKey(quote), quote.id(),
                    (current, other) -> other.compareTo(current) < 0 ? other : current);
        }

        return withoutCollisions.stream()
                .filter(quote -> quote.id().equals(lowestIdPerSpeakerText.get(speakerTextKey(quote))))
                .filter(quote -> wordCount(quote.dialog()) >= MIN_WORDS)
                .filter(quote -> !EDITORIAL_QUOTE_IDS.contains(quote.id()))
                .filter(quote -> !SUSPECT_ATTRIBUTION_QUOTE_IDS.contains(quote.id()))
                .toList();
    }

    private static String textKey(LoreQuote quote) {
        String dialog = quote.dialog() == null ? "" : quote.dialog();

        return WHITESPACE.matcher(dialog).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
    }

    private static String speakerTextKey(LoreQuote quote) {
        return quote.characterId() + "\0" + textKey(quote);
    }

    private static int wordCount(String dialog) {
        Matcher matcher = WORD.matcher(dialog == null ? "" : dialog);
        int count = 0;

        while (matcher.find()) {
            count++;
        }

        return count;
    }
}
