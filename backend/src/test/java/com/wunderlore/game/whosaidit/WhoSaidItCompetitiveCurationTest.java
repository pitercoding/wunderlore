package com.wunderlore.game.whosaidit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.wunderlore.lore.LoreCharacter;
import com.wunderlore.lore.LoreData;
import com.wunderlore.lore.LoreQuote;

// Ids ending in e/f 7-f1 are real records of the editorial audit of the upstream data, with their
// real characters and texts. Ids starting with f2 do not exist upstream: they are fixtures.
class WhoSaidItCompetitiveCurationTest {

    private static final String PREFIX = "5cd96e05de30eff6ebcc";

    private static final String PIPPIN = "5cd99d4bde30eff6ebccfe2e";
    private static final String SAM = "5cd99d4bde30eff6ebccfd0d";
    private static final String GOLLUM = "5cd99d4bde30eff6ebccfe9e";
    private static final String ARAGORN = "5cd99d4bde30eff6ebccfbe6";
    private static final String ELROND = "5cd99d4bde30eff6ebccfcc8";
    private static final String SAURON = "5cd99d4bde30eff6ebccfea5";
    private static final String BOROMIR = "5cd99d4bde30eff6ebccfc57";
    private static final String KING_OF_THE_DEAD = "5cd99d4bde30eff6ebccfe0d";
    private static final String GANDALF = "5cd99d4bde30eff6ebccfea0";
    private static final String MINOR_CHARACTER = "5cdbe49b7ed9587226e794a0";

    private static LoreQuote quote(String suffix, String dialog, String characterId) {
        return new LoreQuote(PREFIX + suffix, dialog, "movie", characterId, PREFIX + suffix);
    }

    private static List<String> ids(List<LoreQuote> quotes) {
        return quotes.stream().map(LoreQuote::id).toList();
    }

    private static String id(String suffix) {
        return PREFIX + suffix;
    }

    private static List<LoreQuote> curate(LoreQuote... quotes) {
        return WhoSaidItCompetitiveCuration.eligibleQuotes(List.of(quotes));
    }

    @Test
    void shouldDropEveryQuoteOfATextAttributedToDifferentCharacters() {
        LoreQuote pippin = quote("ea09", "What are you doing?", PIPPIN);
        LoreQuote sam = quote("ea5c", "What are you doing?", SAM);
        LoreQuote other = quote("f207", "It's talking, Merry. The tree is talking.", PIPPIN);

        assertThat(curate(pippin, sam, other)).containsExactly(other);
    }

    @Test
    void shouldDropEveryCopyOfACollidingTextNotJustOneOfThem() {
        LoreQuote a = quote("ea09", "What are you doing?", PIPPIN);
        LoreQuote b = quote("f201", "What are you doing?", PIPPIN);
        LoreQuote c = quote("ea5c", "What are you doing?", SAM);

        assertThat(curate(a, b, c)).isEmpty();
    }

    @Test
    void shouldNotLetACollisionWithMinorCharacterRemoveARealCharactersQuote() {
        LoreQuote placeholder = quote("e86a", "Come on! Faster!", MINOR_CHARACTER);
        LoreQuote sam = quote("f05a", "Come on faster!", SAM);
        LoreQuote samBis = quote("f20d", "COME ON faster!", SAM);
        Map<String, LoreCharacter> characters = new LinkedHashMap<>();

        characters.put(MINOR_CHARACTER, new LoreCharacter(MINOR_CHARACTER, "MINOR_CHARACTER"));
        characters.put(SAM, new LoreCharacter(SAM, "Samwise Gamgee"));

        List<LoreQuote> all = List.of(placeholder, sam, samBis);
        LoreData data = new LoreData(all, characters, all);

        List<LoreQuote> eligible = WhoSaidItCompetitiveCuration.eligibleQuotes(
                WhoSaidItExclusions.playableQuotes(data));

        // The placeholder is gone before the curation runs, so Sam is the only speaker left
        // for that text; his own case-only copy is dropped as a duplicate.
        assertThat(ids(eligible)).containsExactly(id("f05a"));
    }

    @Test
    void shouldKeepTextsThatDifferOnlyInPunctuation() {
        // Different characters: not a collision.
        LoreQuote pippin = quote("f202", "Where are they?", PIPPIN);
        LoreQuote aragorn = quote("f203", "Where are they!", ARAGORN);
        // The same character: not a duplicate (real records, Gollum).
        LoreQuote withDot = quote("eb74", "Leave now and never come back.", GOLLUM);
        LoreQuote withBang = quote("eb75", "Leave now and never come back!", GOLLUM);

        assertThat(curate(pippin, aragorn, withDot, withBang))
                .containsExactly(pippin, aragorn, withDot, withBang);
    }

    @Test
    void shouldTreatCaseAndWhitespaceAsTheSameText() {
        LoreQuote loud = quote("ea5c", "WHAT  are you   doing?", SAM);
        LoreQuote spaced = quote("ea09", "  what are you doing?  ", PIPPIN);
        LoreQuote nbsp = quote("f204", "What are you doing?", GOLLUM);
        LoreQuote unrelated = quote("f207", "It's talking, Merry. The tree is talking.", PIPPIN);

        assertThat(curate(loud, spaced, nbsp, unrelated)).containsExactly(unrelated);
    }

    @Test
    void shouldKeepOnlyTheLowestIdOfARepeatedTextByTheSameCharacter() {
        // Real records: Sam repeats "Mr Frodo? What is it?" (e822 and ed5f).
        LoreQuote later = quote("ed5f", "Mr Frodo? What is it?", SAM);
        LoreQuote lowest = quote("e822", "Mr Frodo? What is it?", SAM);
        LoreQuote differentCase = quote("f208", "mr frodo?   WHAT is it?", SAM);

        List<LoreQuote> eligible = curate(later, lowest, differentCase);

        assertThat(eligible).containsExactly(lowest);
    }

    @Test
    void shouldNotTreatARepeatByTwoCharactersAsADuplicate() {
        LoreQuote aragorn = quote("f205", "Hold the line, Gandalf.", ARAGORN);
        LoreQuote gandalf = quote("f206", "Hold the line, Gandalf.", GANDALF);

        // Two characters: a collision, so both go, not a duplicate that keeps the lowest id.
        assertThat(curate(aragorn, gandalf)).isEmpty();
    }

    @Test
    void shouldDropQuotesWithFewerThanThreeWords() {
        LoreQuote one = quote("e7e9", "Deagol!!", GOLLUM);
        LoreQuote two = quote("ec1c", "Sam, no!", SAM);
        LoreQuote three = quote("ee15", "Merin le telim.", ARAGORN);
        LoreQuote contraction = quote("f20c", "Don't go. Please.", PIPPIN);

        // An apostrophe is part of a word: "Don't" is one word, so this has three.
        assertThat(curate(one, two, three, contraction)).containsExactly(three, contraction);
    }

    @Test
    void shouldDropTheQuotesTheAuditMarkedWithEditorialDefects() {
        LoreQuote glued = quote("eb61", "I will not leave my daughter hereto die.", ELROND);
        LoreQuote stageDirection = quote("e908",
                "The way is shut! It was made by those who are dead. And the dead keep it. "
                        + "(The Ghostly army closes around them) The way is shut! Now you must die!",
                KING_OF_THE_DEAD);
        LoreQuote ocr = quote("eae9", "The One Ring. lsildur's Bane.", BOROMIR);
        LoreQuote lostAccents = quote("ebdf",
                "He was strong in life. His spirit will find its way to the halls of your fathers. "
                        + "Westu h'l. Fer'u, Th'odred, Fer'u.",
                GANDALF);
        LoreQuote intact = quote("f0ed", "I see you!", SAURON);

        assertThat(curate(glued, stageDirection, ocr, lostAccents, intact)).containsExactly(intact);
    }

    @Test
    void shouldNotRepairTheTextOfTheQuotesItKeeps() {
        LoreQuote untouched = quote("f207", "It's talking, Merry.The tree is talking.", PIPPIN);

        // This text looks glued but is not in the audit set: it is kept exactly as received.
        List<LoreQuote> eligible = curate(untouched);

        assertThat(eligible).hasSize(1);
        assertThat(eligible.get(0).dialog()).isEqualTo("It's talking, Merry.The tree is talking.");
    }

    @Test
    void shouldDropTheQuoteAttributedToSauronThatBelongsToASarumanScene() {
        LoreQuote suspect = quote("edf3", "Build me an army worthy of Mordor.", SAURON);
        LoreQuote legitimate = quote("f0ed", "I see you!", SAURON);

        assertThat(curate(suspect, legitimate)).containsExactly(legitimate);
    }

    @Test
    void shouldKeepIntactQuotesInConstructedLanguages() {
        LoreQuote sindarin = quote("ee15", "Merin le telim.", ARAGORN);
        LoreQuote longer = quote("ee96", "Dartho guin perian. Rych le ad tolthathon.", ARAGORN);

        assertThat(curate(sindarin, longer)).containsExactly(sindarin, longer);
    }

    @Test
    void shouldKeepACharacterWithASingleEligibleQuote() {
        LoreQuote sauron = quote("f0ed", "I see you!", SAURON);
        LoreQuote sam = quote("f207", "It's talking, Merry. The tree is talking.", SAM);

        assertThat(WhoSaidItCompetitiveCuration.eligibleQuotes(List.of(sauron, sam)))
                .extracting(LoreQuote::characterId).containsExactly(SAURON, SAM);
    }

    @Test
    void shouldKeepTheOriginalOrder() {
        LoreQuote c = quote("f20b", "Third quote of the list.", SAM);
        LoreQuote a = quote("f209", "First quote of the list.", SAM);
        LoreQuote b = quote("f20a", "Second quote of the list.", PIPPIN);

        assertThat(curate(c, a, b)).containsExactly(c, a, b);
    }

    @Test
    void shouldKeepTheAuditedEditorialIdsWellFormedAndSeparateFromTheSharedExclusions() {
        assertThat(WhoSaidItCompetitiveCuration.EDITORIAL_QUOTE_IDS).hasSize(139);
        assertThat(WhoSaidItCompetitiveCuration.EDITORIAL_QUOTE_IDS)
                .allMatch(value -> value.matches("[0-9a-f]{24}"));
        assertThat(WhoSaidItCompetitiveCuration.EDITORIAL_QUOTE_IDS)
                .doesNotContainAnyElementsOf(WhoSaidItExclusions.QUOTE_IDS);
        assertThat(WhoSaidItCompetitiveCuration.SUSPECT_ATTRIBUTION_QUOTE_IDS)
                .containsExactly(id("edf3"));
    }
}
