package com.wunderlore.game.whoami;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

// Two parts: the rules the bank enforces when it loads (checked with made-up characters), and
// the real curated content, which must pass them. The real content is also what the editorial
// guards below read.
class WhoAmICharacterBankTest {

    private static final WhoAmICharacterBank REAL = new WhoAmICharacterBank();

    private static List<WhoAmICharacter> withModified(int index, UnaryOperator<WhoAmICharacter> change) {
        List<WhoAmICharacter> all = new ArrayList<>(WhoAmITestData.characters(12));

        all.set(index, change.apply(all.get(index)));

        return all;
    }

    private static WhoAmICharacter copy(
            WhoAmICharacter c, String id, String name, List<String> aliases, List<String> clues) {
        return new WhoAmICharacter(
                id, name, c.difficulty(), c.group(), aliases, clues, c.references(), c.incompatibleWith());
    }

    private static void assertRejected(List<WhoAmICharacter> all, String reason) {
        assertThatThrownBy(() -> new WhoAmICharacterBank(all))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(reason);
    }

    // ---- the rules, with made-up characters ----

    @Test
    void shouldLoadAValidBankAndGroupItByDifficulty() {
        WhoAmICharacterBank bank = WhoAmITestData.bank(12);

        for (WhoAmIDifficulty difficulty : WhoAmIDifficulty.values()) {
            assertThat(bank.count(difficulty)).isEqualTo(12);
            assertThat(bank.find(difficulty)).allMatch(c -> c.difficulty() == difficulty);
        }
    }

    @Test
    void shouldRejectADifficultyTooSmallForAGame() {
        List<WhoAmICharacter> all = WhoAmITestData.characters(12);

        all.remove(all.size() - 1);
        all.remove(all.size() - 1);
        all.remove(all.size() - 1);

        assertRejected(all, "at least 10 are needed");
    }

    @Test
    void shouldRejectDuplicateIdsAndNames() {
        assertRejected(
                withModified(1, c -> copy(c, "test-0", c.name(), c.aliases(), c.clues())),
                "duplicate id");
        assertRejected(
                withModified(1, c -> copy(c, c.id(), "ALDERON", c.aliases(), c.clues())),
                "duplicate name");
    }

    @Test
    void shouldRejectMalformedIds() {
        for (String id : List.of("", "Upper-Case", "has space", "trailing-", "-leading", "a".repeat(41))) {
            assertRejected(
                    withModified(0, c -> copy(c, id, c.name(), c.aliases(), c.clues())),
                    "malformed id");
        }
    }

    @Test
    void shouldRequireExactlyFiveNonBlankDistinctClues() {
        List<String> five = WhoAmITestData.character(0, WhoAmIDifficulty.EASY).clues();

        assertRejected(
                withModified(0, c -> copy(c, c.id(), c.name(), c.aliases(), five.subList(0, 4))),
                "exactly 5 clues");

        List<String> six = new ArrayList<>(five);

        six.add("Hint 6.");

        assertRejected(
                withModified(0, c -> copy(c, c.id(), c.name(), c.aliases(), six)), "exactly 5 clues");

        List<String> blank = new ArrayList<>(five);

        blank.set(2, "  ");

        assertRejected(
                withModified(0, c -> copy(c, c.id(), c.name(), c.aliases(), blank)), "blank");

        List<String> repeated = new ArrayList<>(five);

        repeated.set(3, repeated.get(1));

        assertRejected(
                withModified(0, c -> copy(c, c.id(), c.name(), c.aliases(), repeated)), "repeated clue");

        List<String> noPeriod = new ArrayList<>(five);

        noPeriod.set(0, "No final period");

        assertRejected(
                withModified(0, c -> copy(c, c.id(), c.name(), c.aliases(), noPeriod)), "period");

        List<String> tooLong = new ArrayList<>(five);

        tooLong.set(0, "x".repeat(WhoAmICharacterBank.MAX_CLUE_LENGTH) + ".");

        assertRejected(
                withModified(0, c -> copy(c, c.id(), c.name(), c.aliases(), tooLong)), "longer than");
    }

    @Test
    void shouldRejectAClueThatNamesTheCharacterOrAnAlias() {
        List<String> clues = new ArrayList<>(WhoAmITestData.character(0, WhoAmIDifficulty.EASY).clues());

        clues.set(1, "This is Alderon, of course.");
        assertRejected(
                withModified(0, c -> copy(c, c.id(), c.name(), c.aliases(), clues)), "gives away");

        // Case and accents do not matter.
        clues.set(1, "This is ALDÉRON, of course.");
        assertRejected(
                withModified(0, c -> copy(c, c.id(), "Aldéron", c.aliases(), clues)), "gives away");

        clues.set(1, "They call this one the wolf-king.");
        assertRejected(
                withModified(0, c -> copy(c, c.id(), c.name(), List.of("Wolf King"), clues)),
                "gives away 'wolf king'");

        // A long word of the name counts by itself: "Corvane Test" is given away by "corvane".
        clues.set(1, "Somebody called Corvane did it.");
        assertRejected(
                withModified(2, c -> copy(c, c.id(), "Corvane Test", c.aliases(), clues)),
                "gives away");
    }

    @Test
    void shouldNotTreatPartOfAWordAsAMention() {
        List<String> clues = new ArrayList<>(WhoAmITestData.character(0, WhoAmIDifficulty.EASY).clues());

        // "Alderon" inside a longer word is not the name.
        clues.set(1, "Nothing to do with Alderonians here.");

        new WhoAmICharacterBank(
                withModified(0, c -> copy(c, c.id(), c.name(), c.aliases(), clues)));
    }

    @Test
    void shouldRejectAnAliasThatBelongsToAnotherCharacterOrNamesIt() {
        assertRejected(
                withModified(1, c -> copy(c, c.id(), c.name(), List.of("Alderon"), c.clues())),
                "also belongs to");

        // Two characters may share a word of their names, but not a whole alias.
        List<WhoAmICharacter> shared = new ArrayList<>(WhoAmITestData.characters(12));

        for (int i = 0; i < 2; i++) {
            WhoAmICharacter c = shared.get(i);

            shared.set(i, copy(c, c.id(), c.name(), List.of("The Shared One"), c.clues()));
        }

        assertRejected(shared, "also belongs to");
    }

    @Test
    void shouldRequireWellFormedReferences() {
        WhoAmICharacter c = WhoAmITestData.character(0, WhoAmIDifficulty.EASY);

        for (List<WhoAmICharacter.Reference> references : List.of(
                List.<WhoAmICharacter.Reference>of(),
                List.of(new WhoAmICharacter.Reference("Source", "http://insecure.example", "Fact.")),
                List.of(new WhoAmICharacter.Reference("Source", null, "Fact.")),
                List.of(new WhoAmICharacter.Reference(" ", "https://example.org", "Fact.")),
                List.of(new WhoAmICharacter.Reference("Source", "https://example.org", "")))) {
            assertRejected(
                    withModified(0, x -> new WhoAmICharacter(
                            c.id(), c.name(), c.difficulty(), c.group(), c.aliases(), c.clues(),
                            references, c.incompatibleWith())),
                    c.id());
        }
    }

    @Test
    void shouldRejectUnknownIncompatibilities() {
        WhoAmICharacter c = WhoAmITestData.character(0, WhoAmIDifficulty.EASY);

        assertRejected(
                withModified(0, x -> new WhoAmICharacter(
                        c.id(), c.name(), c.difficulty(), c.group(), c.aliases(), c.clues(),
                        c.references(), List.of("nobody"))),
                "incompatibleWith");
        assertRejected(
                withModified(0, x -> new WhoAmICharacter(
                        c.id(), c.name(), c.difficulty(), c.group(), c.aliases(), c.clues(),
                        c.references(), List.of(c.id()))),
                "incompatibleWith");
    }

    @Test
    void shouldExcludeIncompatibleCharactersFromWrongAnswersInBothDirections() {
        List<WhoAmICharacter> all = new ArrayList<>(WhoAmITestData.characters(12));
        WhoAmICharacter first = all.get(0);

        all.set(0, new WhoAmICharacter(
                first.id(), first.name(), first.difficulty(), first.group(), first.aliases(),
                first.clues(), first.references(), List.of("test-1")));

        WhoAmICharacterBank bank = new WhoAmICharacterBank(all);

        assertThat(bank.wrongAnswerCandidates(bank.find(WhoAmIDifficulty.EASY).get(0)))
                .extracting(WhoAmICharacter::id)
                .doesNotContain("test-0", "test-1")
                .hasSize(10);
        assertThat(bank.wrongAnswerCandidates(bank.find(WhoAmIDifficulty.EASY).get(1)))
                .extracting(WhoAmICharacter::id)
                .doesNotContain("test-1", "test-0");
    }

    // ---- the real content ----

    @Test
    void shouldLoadTheCuratedContent() {
        for (WhoAmIDifficulty difficulty : WhoAmIDifficulty.values()) {
            assertThat(REAL.count(difficulty))
                    .as(difficulty.name())
                    .isGreaterThanOrEqualTo(10);
        }
    }

    @Test
    void shouldHaveStableUniqueIdsAndCanonicalNames() {
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();

        for (WhoAmIDifficulty difficulty : WhoAmIDifficulty.values()) {
            for (WhoAmICharacter character : REAL.find(difficulty)) {
                assertThat(ids.add(character.id())).as(character.id()).isTrue();
                assertThat(names.add(character.name())).as(character.name()).isTrue();
                assertThat(character.id()).matches("[a-z0-9]+(-[a-z0-9]+)*");
            }
        }
    }

    @Test
    void shouldGiveEveryCharacterFiveCluesAndAFactualReference() {
        for (WhoAmIDifficulty difficulty : WhoAmIDifficulty.values()) {
            for (WhoAmICharacter character : REAL.find(difficulty)) {
                assertThat(character.clues()).as(character.id()).hasSize(5);
                assertThat(character.references()).as(character.id()).isNotEmpty();
                assertThat(character.references())
                        .allSatisfy(reference -> {
                            assertThat(reference.url()).startsWith("https://");
                            assertThat(reference.supports()).isNotBlank();
                        });
            }
        }
    }

    @Test
    void shouldKeepTheNameOutOfEveryClueOfTheCuratedContent() {
        // The bank already refuses this at startup; the test names the rule and reads it back.
        for (WhoAmIDifficulty difficulty : WhoAmIDifficulty.values()) {
            for (WhoAmICharacter character : REAL.find(difficulty)) {
                for (String clue : character.clues()) {
                    for (String term : WhoAmICharacterBank.namesOf(character)) {
                        assertThat(WhoAmICharacterBank.mentions(clue, term))
                                .as("%s: '%s' in '%s'", character.id(), term, clue)
                                .isFalse();
                    }
                }
            }
        }
    }

    @Test
    void shouldNotLetAClueMentionAnotherCharacterOfTheSameDifficultyByItsFullName() {
        // A clue that names someone else is fine in itself, but the player may mistake it for
        // the answer; the curated clues name other characters only through relations, never by
        // an exact full name of a character that can sit next to this one in the options.
        for (WhoAmIDifficulty difficulty : WhoAmIDifficulty.values()) {
            List<WhoAmICharacter> characters = REAL.find(difficulty);

            for (WhoAmICharacter character : characters) {
                for (WhoAmICharacter other : characters) {
                    if (other.id().equals(character.id())) {
                        continue;
                    }

                    for (String clue : character.clues()) {
                        assertThat(WhoAmICharacterBank.mentions(
                                clue, WhoAmICharacterBank.normalize(other.name())))
                                .as("%s names %s: %s", character.id(), other.id(), clue)
                                .isFalse();
                    }
                }
            }
        }
    }

    @Test
    void shouldGiveEveryCharacterEnoughWrongAnswers() {
        for (WhoAmIDifficulty difficulty : WhoAmIDifficulty.values()) {
            for (WhoAmICharacter character : REAL.find(difficulty)) {
                assertThat(REAL.wrongAnswerCandidates(character))
                        .as(character.id())
                        .hasSizeGreaterThanOrEqualTo(7)
                        .allMatch(other -> other.difficulty() == difficulty);
            }
        }
    }
}
