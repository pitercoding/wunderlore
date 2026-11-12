package com.wunderlore.game.whoami;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

// The curated characters, versioned with the code: one JSON file under
// src/main/resources/who-am-i/. They are read once at startup and kept immutable, and every rule
// below is checked then, so a malformed character stops the application from starting instead of
// failing in the middle of a game. Adding a character means appending an entry to that file.
@Component
public class WhoAmICharacterBank {

    static final String RESOURCE = "who-am-i/characters.json";

    public static final int CLUES_PER_CHARACTER = 5;

    // The least a difficulty needs to build one game of ten different characters.
    public static final int MIN_PER_DIFFICULTY = 10;

    // Each character needs at least this many compatible wrong answers, so the drawer has a real
    // choice: only three are shown, drawn once per round.
    static final int MIN_WRONG_ANSWER_CANDIDATES = 7;

    static final int MAX_CLUE_LENGTH = 220;

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    // Words of a name shorter than this are not treated as names by themselves ("Tom").
    private static final int MIN_NAME_WORD_LENGTH = 4;

    private record CharacterFile(List<WhoAmICharacter> characters) {
    }

    private final Map<WhoAmIDifficulty, List<WhoAmICharacter>> byDifficulty =
            new EnumMap<>(WhoAmIDifficulty.class);

    private final Map<String, WhoAmICharacter> byId = new HashMap<>();

    @Autowired
    public WhoAmICharacterBank() {
        this(loadFromClasspath());
    }

    WhoAmICharacterBank(List<WhoAmICharacter> all) {
        for (WhoAmIDifficulty difficulty : WhoAmIDifficulty.values()) {
            byDifficulty.put(difficulty, new ArrayList<>());
        }

        Set<String> names = new HashSet<>();
        Map<String, String> ownerOfName = new HashMap<>();

        for (WhoAmICharacter character : all) {
            validate(character);

            if (byId.put(character.id(), character) != null) {
                throw invalid(character, "duplicate id");
            }

            if (!names.add(normalize(character.name()))) {
                throw invalid(character, "duplicate name");
            }

            byDifficulty.get(character.difficulty()).add(character);
        }

        // A name or alias may identify one character only: otherwise two options (or an option
        // and a clue) could be the same character under different names.
        for (WhoAmICharacter character : all) {
            for (String term : identifiersOf(character)) {
                String owner = ownerOfName.putIfAbsent(term, character.id());

                if (owner != null && !owner.equals(character.id())) {
                    throw invalid(character, "name or alias '" + term + "' also belongs to " + owner);
                }
            }
        }

        for (WhoAmICharacter character : all) {
            for (String other : character.incompatibleWith()) {
                if (!byId.containsKey(other) || other.equals(character.id())) {
                    throw invalid(character, "unknown or self incompatibleWith: " + other);
                }
            }

            if (wrongAnswerCandidates(character).size() < MIN_WRONG_ANSWER_CANDIDATES
                    && !byDifficulty.get(character.difficulty()).isEmpty()) {
                throw invalid(character, "fewer than " + MIN_WRONG_ANSWER_CANDIDATES
                        + " compatible wrong answers in its difficulty");
            }
        }

        for (WhoAmIDifficulty difficulty : WhoAmIDifficulty.values()) {
            int count = byDifficulty.get(difficulty).size();

            if (count < MIN_PER_DIFFICULTY) {
                throw new IllegalArgumentException(
                        difficulty + " has " + count + " characters, at least "
                                + MIN_PER_DIFFICULTY + " are needed");
            }
        }
    }

    // A copy; callers shuffle it freely.
    public List<WhoAmICharacter> find(WhoAmIDifficulty difficulty) {
        return List.copyOf(byDifficulty.get(difficulty));
    }

    public int count(WhoAmIDifficulty difficulty) {
        return byDifficulty.get(difficulty).size();
    }

    // The characters of the same difficulty that may be shown next to this one as wrong answers:
    // everyone but itself and the ones it is declared incompatible with (in either direction).
    public List<WhoAmICharacter> wrongAnswerCandidates(WhoAmICharacter character) {
        List<WhoAmICharacter> candidates = new ArrayList<>();

        for (WhoAmICharacter other : byDifficulty.get(character.difficulty())) {
            if (!other.id().equals(character.id())
                    && !character.incompatibleWith().contains(other.id())
                    && !other.incompatibleWith().contains(character.id())) {
                candidates.add(other);
            }
        }

        return candidates;
    }

    // What identifies the character: its name and its aliases, normalized.
    static List<String> identifiersOf(WhoAmICharacter character) {
        List<String> terms = new ArrayList<>();

        terms.add(normalize(character.name()));

        for (String alias : character.aliases()) {
            terms.add(normalize(alias));
        }

        return terms;
    }

    // Everything that names the character: its name, its aliases and the long words of its name,
    // normalized. A clue containing any of them as a whole word gives the answer away.
    static List<String> namesOf(WhoAmICharacter character) {
        List<String> terms = new ArrayList<>();

        terms.add(normalize(character.name()));

        for (String word : normalize(character.name()).split(" ")) {
            if (word.length() >= MIN_NAME_WORD_LENGTH) {
                terms.add(word);
            }
        }

        for (String alias : character.aliases()) {
            terms.add(normalize(alias));
        }

        return terms;
    }

    // Lower case, without accents, with every run of other characters turned into one space.
    static String normalize(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        return decomposed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
    }

    // Whole-word containment on normalized text.
    static boolean mentions(String text, String normalizedTerm) {
        return !normalizedTerm.isEmpty()
                && (" " + normalize(text) + " ").contains(" " + normalizedTerm + " ");
    }

    private static void validate(WhoAmICharacter character) {
        if (character.id() == null || !ID_PATTERN.matcher(character.id()).matches()
                || character.id().length() > 40) {
            throw invalid(character, "malformed id");
        }

        if (isBlank(character.name()) || !character.name().equals(character.name().strip())) {
            throw invalid(character, "blank or untrimmed name");
        }

        if (character.difficulty() == null) {
            throw invalid(character, "missing difficulty");
        }

        if (isBlank(character.group())) {
            throw invalid(character, "missing group");
        }

        for (String alias : character.aliases()) {
            if (isBlank(alias)) {
                throw invalid(character, "blank alias");
            }
        }

        if (character.clues().size() != CLUES_PER_CHARACTER) {
            throw invalid(character, "needs exactly " + CLUES_PER_CHARACTER + " clues");
        }

        List<String> terms = namesOf(character);
        Set<String> seen = new HashSet<>();

        for (String clue : character.clues()) {
            if (isBlank(clue) || !clue.equals(clue.strip())) {
                throw invalid(character, "blank or untrimmed clue");
            }

            if (clue.length() > MAX_CLUE_LENGTH) {
                throw invalid(character, "clue longer than " + MAX_CLUE_LENGTH + " characters");
            }

            if (!clue.endsWith(".")) {
                throw invalid(character, "clue does not end with a period: " + clue);
            }

            if (!seen.add(normalize(clue))) {
                throw invalid(character, "repeated clue");
            }

            for (String term : terms) {
                if (mentions(clue, term)) {
                    throw invalid(character, "a clue gives away '" + term + "': " + clue);
                }
            }
        }

        if (character.references().isEmpty()) {
            throw invalid(character, "needs at least one reference");
        }

        for (WhoAmICharacter.Reference reference : character.references()) {
            if (isBlank(reference.source()) || isBlank(reference.supports())
                    || reference.url() == null || !reference.url().startsWith("https://")) {
                throw invalid(character, "incomplete reference");
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static IllegalArgumentException invalid(WhoAmICharacter character, String problem) {
        return new IllegalArgumentException("Who Am I? character " + character.id() + ": " + problem);
    }

    private static List<WhoAmICharacter> loadFromClasspath() {
        JsonMapper mapper = JsonMapper.builder().build();

        try (InputStream stream = new ClassPathResource(RESOURCE).getInputStream()) {
            return mapper.readValue(stream, CharacterFile.class).characters();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read " + RESOURCE, exception);
        }
    }
}
