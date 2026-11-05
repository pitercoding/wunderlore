package com.wunderlore.game.whoami;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.wunderlore.game.whoami.WhoAmIGame.Option;
import com.wunderlore.game.whoami.WhoAmIGame.Options;

// Small builders so tests do not depend on the real content.
final class WhoAmITestData {

    static final Instant START = Instant.parse("2026-01-01T12:00:00Z");

    // Made-up names, none of them a word that appears in the generated clues.
    private static final String[] NAMES = {
        "Alderon", "Brynmar", "Corvane", "Dunwick", "Eskarra", "Fennolt", "Gorvald", "Hestira",
        "Ildaric", "Jorvane", "Kelmora", "Lundrik", "Marvolt", "Nerisha", "Ostrand", "Pelmira",
        "Quendal", "Rovinar", "Sabrane", "Torvald", "Ulmeric", "Vardane", "Wenmora", "Xandrik",
        "Yoriel", "Zephran", "Aldwyn", "Bevoran", "Caldris", "Dorwen", "Elmoran", "Falwick",
        "Garmund", "Helvane", "Isoldar", "Jaskren"
    };

    private WhoAmITestData() {
    }

    static WhoAmICharacter character(int index, WhoAmIDifficulty difficulty) {
        String name = NAMES[index];
        List<String> clues = new ArrayList<>();

        for (int clue = 1; clue <= WhoAmICharacterBank.CLUES_PER_CHARACTER; clue++) {
            clues.add("Hint " + clue + " about subject " + index + ".");
        }

        return new WhoAmICharacter(
                "test-" + index,
                name,
                difficulty,
                index % 2 == 0 ? "even" : "odd",
                List.of(name + " the Test"),
                clues,
                List.of(new WhoAmICharacter.Reference("Test source", "https://example.org/" + index, "A test fact.")),
                List.of());
    }

    // perDifficulty characters for each difficulty, named from the shared list.
    static List<WhoAmICharacter> characters(int perDifficulty) {
        List<WhoAmICharacter> all = new ArrayList<>();
        int index = 0;

        for (WhoAmIDifficulty difficulty : WhoAmIDifficulty.values()) {
            for (int i = 0; i < perDifficulty; i++) {
                all.add(character(index++, difficulty));
            }
        }

        return all;
    }

    static WhoAmICharacterBank bank(int perDifficulty) {
        return new WhoAmICharacterBank(characters(perDifficulty));
    }

    // The correct option is always "a"; the other three are fixed fillers that are not
    // characters of the game. Records what it was asked to exclude.
    static final class FixedDrawer implements WhoAmIGame.OptionDrawer {

        final List<Set<String>> excluded = new ArrayList<>();

        @Override
        public Options draw(WhoAmICharacter correct, Set<String> excludedCharacterIds) {
            excluded.add(excludedCharacterIds);

            Map<String, String> ids = new HashMap<>();

            ids.put("a", correct.id());
            ids.put("b", "filler-b");
            ids.put("c", "filler-c");
            ids.put("d", "filler-d");

            return new Options(
                    List.of(
                            new Option("a", correct.name()),
                            new Option("b", "Filler B"),
                            new Option("c", "Filler C"),
                            new Option("d", "Filler D")),
                    "a",
                    ids);
        }
    }

    static WhoAmIGame game(WhoAmIDifficulty difficulty, FixedDrawer drawer, Instant createdAt) {
        List<WhoAmICharacter> characters = new ArrayList<>();

        for (int i = 0; i < WhoAmIGame.TOTAL_ROUNDS; i++) {
            characters.add(character(i, difficulty));
        }

        return new WhoAmIGame(difficulty, characters, drawer, createdAt);
    }

    static WhoAmIGame game(WhoAmIDifficulty difficulty, Instant createdAt) {
        return game(difficulty, new FixedDrawer(), createdAt);
    }

    static final class MutableClock extends Clock {

        private final AtomicReference<Instant> current;

        MutableClock(Instant initial) {
            this.current = new AtomicReference<>(initial);
        }

        void advance(Duration duration) {
            current.updateAndGet(instant -> instant.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}
