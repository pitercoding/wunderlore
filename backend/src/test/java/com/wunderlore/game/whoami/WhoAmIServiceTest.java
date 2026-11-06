package com.wunderlore.game.whoami;

import static com.wunderlore.game.whoami.WhoAmITestData.START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.wunderlore.game.whoami.WhoAmIException.Reason;
import com.wunderlore.game.whoami.WhoAmIGame.AnswerOutcome;
import com.wunderlore.game.whoami.WhoAmIGame.Options;

// Selection of characters and options, on the real curated content with a seeded generator, so
// every property is checked over many games rather than one lucky draw.
class WhoAmIServiceTest {

    private static final WhoAmICharacterBank BANK = new WhoAmICharacterBank();

    private static final Clock CLOCK = Clock.fixed(START, ZoneOffset.UTC);

    private final WhoAmIService service = new WhoAmIService(BANK, new Random(42), CLOCK);

    // What a player cannot do, and a test can: the correct option of the current clue, read from
    // the bank by matching the first clue.
    private WhoAmICharacter characterOf(WhoAmIGame game) {
        String firstClue = game.currentRound().clues().get(0);

        return java.util.stream.Stream.of(WhoAmIDifficulty.values())
                .flatMap(d -> BANK.find(d).stream())
                .filter(c -> c.clues().get(0).equals(firstClue))
                .findFirst()
                .orElseThrow();
    }

    private static String correctOptionId(WhoAmIGame.RoundView view, WhoAmICharacter character) {
        return view.options().stream()
                .filter(option -> option.text().equals(character.name()))
                .findFirst()
                .orElseThrow()
                .id();
    }

    @Test
    void shouldPlayTenDifferentCharactersOfTheChosenDifficulty() {
        for (WhoAmIDifficulty difficulty : WhoAmIDifficulty.values()) {
            for (int run = 0; run < 25; run++) {
                WhoAmIGame game = service.createGame(difficulty);
                Set<String> names = new HashSet<>();

                for (int round = 1; round <= WhoAmIGame.TOTAL_ROUNDS; round++) {
                    WhoAmIGame.RoundView view = game.currentRound();
                    WhoAmICharacter character = characterOf(game);

                    assertThat(view.difficulty()).isEqualTo(difficulty);
                    assertThat(view.roundNumber()).isEqualTo(round);
                    assertThat(character.difficulty()).isEqualTo(difficulty);
                    assertThat(names.add(character.name()))
                            .as("%s repeated in one game", character.name())
                            .isTrue();

                    game.answer(round, 1, correctOptionId(view, character));
                }

                assertThat(game.isFinished()).isTrue();
                assertThat(game.result().score()).isEqualTo(1000);
            }
        }
    }

    @Test
    void shouldVaryWhichCharactersAGameUses() {
        Set<Set<String>> selections = new HashSet<>();

        for (int run = 0; run < 30; run++) {
            WhoAmIGame game = service.createGame(WhoAmIDifficulty.EASY);
            Set<String> names = new HashSet<>();

            for (int round = 1; round <= 10; round++) {
                WhoAmICharacter character = characterOf(game);

                names.add(character.name());
                game.answer(round, 1, correctOptionId(game.currentRound(), character));
            }

            selections.add(names);
        }

        assertThat(selections.size()).isGreaterThan(1);
    }

    @Test
    void shouldOfferFourDistinctOptionsWithExactlyOneCorrectOnEveryClue() {
        for (WhoAmIDifficulty difficulty : WhoAmIDifficulty.values()) {
            for (WhoAmICharacter character : BANK.find(difficulty)) {
                Set<String> excluded = new HashSet<>();

                for (int clue = 1; clue <= 5; clue++) {
                    Options options = service.drawOptions(character, Set.copyOf(excluded));
                    List<String> names = options.options().stream()
                            .map(WhoAmIGame.Option::text)
                            .toList();
                    List<String> ids = options.options().stream().map(WhoAmIGame.Option::id).toList();

                    assertThat(options.options()).hasSize(4);
                    assertThat(ids).containsExactlyInAnyOrder("a", "b", "c", "d");
                    assertThat(names).doesNotHaveDuplicates();
                    assertThat(names.stream().filter(character.name()::equals)).hasSize(1);
                    assertThat(options.characterIdByOption().values())
                            .doesNotHaveDuplicates()
                            .containsOnlyOnce(character.id());
                    assertThat(options.characterIdByOption().get(options.correctOptionId()))
                            .isEqualTo(character.id());

                    // Wrong options: the same difficulty, never an alias of the right one, never
                    // a ruled-out character.
                    for (String wrongId : options.characterIdByOption().values()) {
                        if (wrongId.equals(character.id())) {
                            continue;
                        }

                        WhoAmICharacter wrong = BANK.wrongAnswerCandidates(character).stream()
                                .filter(c -> c.id().equals(wrongId))
                                .findFirst()
                                .orElseThrow();

                        assertThat(wrong.difficulty()).isEqualTo(difficulty);
                        assertThat(excluded).doesNotContain(wrongId);
                        assertThat(WhoAmICharacterBank.identifiersOf(wrong))
                                .doesNotContainAnyElementsOf(WhoAmICharacterBank.identifiersOf(character));
                    }

                    // Rule out one wrong option, as a wrong answer would.
                    options.characterIdByOption().values().stream()
                            .filter(id -> !id.equals(character.id()))
                            .findFirst()
                            .ifPresent(excluded::add);
                }
            }
        }
    }

    @Test
    void shouldNotLeakTheCorrectOptionByTheIntersectionOfTheOptionsAcrossClues() {
        for (WhoAmIDifficulty difficulty : WhoAmIDifficulty.values()) {
            for (int run = 0; run < 25; run++) {
                WhoAmIGame game = service.createGame(difficulty);

                for (int round = 1; round <= WhoAmIGame.TOTAL_ROUNDS; round++) {
                    WhoAmICharacter character = characterOf(game);
                    String correctId = correctOptionId(game.currentRound(), character);
                    List<WhoAmIGame.Option> shown = game.currentRound().options();
                    Set<String> common = new HashSet<>();

                    shown.forEach(option -> common.add(option.text()));

                    // Four wrong answers in a row, each one on a different option.
                    List<String> wrongIds = shown.stream()
                            .map(WhoAmIGame.Option::id)
                            .filter(id -> !id.equals(correctId))
                            .toList();

                    for (int clue = 1; clue <= 3; clue++) {
                        AnswerOutcome outcome = game.answer(round, clue, wrongIds.get(clue - 1));

                        assertThat(outcome.next().options()).isEqualTo(shown);
                        common.retainAll(outcome.next().options().stream()
                                .map(WhoAmIGame.Option::text)
                                .toList());
                    }

                    // Nothing was narrowed down: the intersection is still all four options.
                    assertThat(common).hasSize(4);

                    game.answer(round, 4, correctId);
                }

                assertThat(game.isFinished()).isTrue();
            }
        }
    }

    @Test
    void shouldPlaceTheCorrectOptionInDifferentPositions() {
        WhoAmICharacter character = BANK.find(WhoAmIDifficulty.MEDIUM).get(0);
        Set<String> positions = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            positions.add(service.drawOptions(character, Set.of()).correctOptionId());
        }

        assertThat(positions).containsExactlyInAnyOrder("a", "b", "c", "d");
    }

    @Test
    void shouldPreferAWrongOptionOfTheSameKind() {
        // Gandalf, Saruman and Radagast are the wizards of the Easy bank: one of the other two
        // is always among the options, whichever one the draw picks.
        WhoAmICharacter gandalf = BANK.find(WhoAmIDifficulty.EASY).stream()
                .filter(c -> c.id().equals("gandalf"))
                .findFirst()
                .orElseThrow();
        Set<String> otherWizards = new HashSet<>();

        for (WhoAmICharacter c : BANK.find(WhoAmIDifficulty.EASY)) {
            if (c.group().equals(gandalf.group()) && !c.id().equals(gandalf.id())) {
                otherWizards.add(c.name());
            }
        }

        assertThat(otherWizards).containsExactlyInAnyOrder("Saruman", "Radagast");

        for (int i = 0; i < 50; i++) {
            assertThat(service.drawOptions(gandalf, Set.of()).options())
                    .extracting(WhoAmIGame.Option::text)
                    .containsAnyElementsOf(otherWizards);
        }
    }

    @Test
    void shouldNeverOfferAnExcludedCharacterAndStillFindWrongAnswersAfterFourGuesses() {
        WhoAmICharacter character = BANK.find(WhoAmIDifficulty.HARD).get(0);
        List<WhoAmICharacter> candidates = new ArrayList<>(BANK.wrongAnswerCandidates(character));
        Set<String> excluded = new HashSet<>();

        for (int i = 0; i < 4; i++) {
            excluded.add(candidates.get(i).id());
        }

        for (int run = 0; run < 50; run++) {
            assertThat(service.drawOptions(character, excluded).characterIdByOption().values())
                    .doesNotContainAnyElementsOf(excluded);
        }
    }

    @Test
    void shouldRefuseToBuildAShorterGame() {
        // A bank cannot be built below ten per difficulty, so exercise the guard with a mock-like
        // bank that reports fewer: nine characters in one difficulty.
        WhoAmICharacterBank small = new WhoAmICharacterBank(WhoAmITestData.characters(10)) {
            @Override
            public List<WhoAmICharacter> find(WhoAmIDifficulty difficulty) {
                return super.find(difficulty).subList(0, 9);
            }
        };
        WhoAmIService shortService = new WhoAmIService(small, new Random(1), CLOCK);

        assertThatThrownBy(() -> shortService.createGame(WhoAmIDifficulty.EASY))
                .isInstanceOfSatisfying(WhoAmIException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.NOT_ENOUGH_CHARACTERS));
    }

    @Test
    void shouldMoveOnAfterEveryWrongAnswerUntilTheFifthClue() {
        WhoAmIGame game = service.createGame(WhoAmIDifficulty.HARD);
        WhoAmICharacter character = characterOf(game);

        for (int clue = 1; clue <= 5; clue++) {
            WhoAmIGame.RoundView view = game.currentRound();
            String wrong = view.options().stream()
                    .filter(option -> !option.text().equals(character.name()))
                    .findFirst()
                    .orElseThrow()
                    .id();

            assertThat(view.clueNumber()).isEqualTo(clue);
            assertThat(view.clues()).isEqualTo(character.clues().subList(0, clue));

            AnswerOutcome outcome = game.answer(1, clue, wrong);

            assertThat(outcome.correct()).isFalse();
            assertThat(outcome.roundFinished()).isEqualTo(clue == 5);
        }

        assertThat(game.currentRound().roundNumber()).isEqualTo(2);
    }

    @Test
    void shouldNeverOfferACharacterDeclaredIncompatibleWithTheCorrectOne() {
        for (WhoAmIDifficulty difficulty : WhoAmIDifficulty.values()) {
            for (WhoAmICharacter character : BANK.find(difficulty)) {
                for (int draw = 0; draw < 40; draw++) {
                    Options options = service.drawOptions(character, Set.of());

                    for (String shownId : options.characterIdByOption().values()) {
                        if (shownId.equals(character.id())) {
                            continue;
                        }

                        WhoAmICharacter shown = BANK.find(difficulty).stream()
                                .filter(c -> c.id().equals(shownId))
                                .findFirst()
                                .orElseThrow();

                        assertThat(character.incompatibleWith()).as(character.id()).doesNotContain(shownId);
                        assertThat(shown.incompatibleWith()).as(shownId).doesNotContain(character.id());
                    }
                }
            }
        }
    }

    @Test
    void shouldKeepTheSameFourOptionsOnAllFiveCluesForEveryCuratedCharacter() {
        for (WhoAmIDifficulty difficulty : WhoAmIDifficulty.values()) {
            List<WhoAmICharacter> all = BANK.find(difficulty);
            int size = WhoAmIGame.TOTAL_ROUNDS;

            // Two windows that together cover every character of the difficulty.
            for (List<WhoAmICharacter> window : List.of(
                    all.subList(0, size), all.subList(all.size() - size, all.size()))) {
                WhoAmIGame game = new WhoAmIGame(difficulty, window, service::drawOptions, START);

                for (int round = 1; round <= size; round++) {
                    WhoAmICharacter character = window.get(round - 1);
                    WhoAmIGame.RoundView view = game.currentRound();
                    String correctId = correctOptionId(view, character);
                    List<WhoAmIGame.Option> shown = view.options();
                    List<String> wrongIds = shown.stream()
                            .map(WhoAmIGame.Option::id)
                            .filter(id -> !id.equals(correctId))
                            .toList();

                    assertThat(shown).as(character.id()).hasSize(4);

                    for (int clue = 1; clue <= 3; clue++) {
                        AnswerOutcome outcome = game.answer(round, clue, wrongIds.get(clue - 1));

                        assertThat(outcome.next().options()).as(character.id()).isEqualTo(shown);
                    }

                    // Clue 4 still shows the same four options; a correct answer ends the round.
                    assertThat(game.currentRound().options()).as(character.id()).isEqualTo(shown);
                    game.answer(round, 4, correctId);
                }

                assertThat(game.isFinished()).isTrue();
            }
        }
    }
}
