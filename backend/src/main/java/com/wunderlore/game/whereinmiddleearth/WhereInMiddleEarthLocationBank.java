package com.wunderlore.game.whereinmiddleearth;

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

// The curated places, versioned with the code: one JSON file under
// src/main/resources/where-in-middle-earth/. They are read once at startup and kept immutable, and
// every rule below is checked then, so a malformed place stops the application from starting
// instead of failing in the middle of a game. Adding a place means appending an entry to that
// file (and a line to REFERENCES.md next to it).
//
// The coordinates are in the map's own system: a viewBox of MAP_WIDTH x MAP_HEIGHT, x to the east
// and y to the south, the same one the frontend draws in (contracts/where-in-middle-earth).
@Component
public class WhereInMiddleEarthLocationBank {

    static final String RESOURCE = "where-in-middle-earth/locations.json";

    public static final double MAP_WIDTH = 1000.0;

    public static final double MAP_HEIGHT = 700.0;

    // The least a difficulty needs to build one game of ten different places.
    public static final int MIN_PER_DIFFICULTY = 10;

    // Two places of one difficulty must be further apart than this, so the EXCELLENT disc of one
    // never contains the other and a click cannot be best for two questions at once.
    static final double MIN_SEPARATION = 2 * WhereInMiddleEarthAccuracy.EXCELLENT_RADIUS;

    static final int MAX_DESCRIPTION_LENGTH = 140;

    static final int MAX_EXPLANATION_LENGTH = 420;

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    public record Region(String id, String name) {
    }

    private record LocationFile(List<Region> regions, List<WhereInMiddleEarthLocation> locations) {
    }

    private final Map<WhereInMiddleEarthDifficulty, List<WhereInMiddleEarthLocation>> byDifficulty =
            new EnumMap<>(WhereInMiddleEarthDifficulty.class);

    private final Map<String, WhereInMiddleEarthLocation> byId = new HashMap<>();

    private final Map<String, String> regionNames = new HashMap<>();

    @Autowired
    public WhereInMiddleEarthLocationBank() {
        this(loadFromClasspath());
    }

    private WhereInMiddleEarthLocationBank(LocationFile file) {
        this(file.regions(), file.locations());
    }

    WhereInMiddleEarthLocationBank(List<Region> regions, List<WhereInMiddleEarthLocation> all) {
        for (WhereInMiddleEarthDifficulty difficulty : WhereInMiddleEarthDifficulty.values()) {
            byDifficulty.put(difficulty, new ArrayList<>());
        }

        for (Region region : regions) {
            if (isBlank(region.id()) || isBlank(region.name())
                    || regionNames.put(region.id(), region.name()) != null) {
                throw new IllegalArgumentException(
                        "Where in Middle-earth: malformed or repeated region " + region.id());
            }
        }

        Set<String> names = new HashSet<>();

        for (WhereInMiddleEarthLocation location : all) {
            validate(location);

            if (byId.put(location.id(), location) != null) {
                throw invalid(location, "duplicate id");
            }

            if (!names.add(normalize(location.name()))) {
                throw invalid(location, "duplicate name");
            }

            byDifficulty.get(location.difficulty()).add(location);
        }

        for (WhereInMiddleEarthDifficulty difficulty : WhereInMiddleEarthDifficulty.values()) {
            List<WhereInMiddleEarthLocation> pool = byDifficulty.get(difficulty);

            if (pool.size() < MIN_PER_DIFFICULTY) {
                throw new IllegalArgumentException(
                        difficulty + " has " + pool.size() + " places, at least "
                                + MIN_PER_DIFFICULTY + " are needed");
            }

            for (int i = 0; i < pool.size(); i++) {
                for (int j = i + 1; j < pool.size(); j++) {
                    if (distance(pool.get(i), pool.get(j)) <= MIN_SEPARATION) {
                        throw invalid(pool.get(i), "too close to " + pool.get(j).id());
                    }
                }
            }
        }
    }

    // A copy; callers shuffle it freely.
    public List<WhereInMiddleEarthLocation> find(WhereInMiddleEarthDifficulty difficulty) {
        return List.copyOf(byDifficulty.get(difficulty));
    }

    public int count(WhereInMiddleEarthDifficulty difficulty) {
        return byDifficulty.get(difficulty).size();
    }

    public String regionName(WhereInMiddleEarthLocation location) {
        return regionNames.get(location.region());
    }

    public List<Region> regions() {
        return regionNames.entrySet().stream()
                .map(entry -> new Region(entry.getKey(), entry.getValue()))
                .toList();
    }

    static double distance(double x1, double y1, double x2, double y2) {
        return Math.hypot(x1 - x2, y1 - y2);
    }

    private static double distance(
            WhereInMiddleEarthLocation first, WhereInMiddleEarthLocation second) {
        return distance(first.x(), first.y(), second.x(), second.y());
    }

    // True for a finite position inside the map, borders included.
    static boolean isInsideMap(double x, double y) {
        return Double.isFinite(x) && Double.isFinite(y)
                && x >= 0 && x <= MAP_WIDTH && y >= 0 && y <= MAP_HEIGHT;
    }

    // Lower case, without accents, with every run of other characters turned into one space.
    static String normalize(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        return decomposed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
    }

    private void validate(WhereInMiddleEarthLocation location) {
        if (location.id() == null || !ID_PATTERN.matcher(location.id()).matches()
                || location.id().length() > 40) {
            throw invalid(location, "malformed id");
        }

        if (isBlank(location.name()) || !location.name().equals(location.name().strip())) {
            throw invalid(location, "blank or untrimmed name");
        }

        if (location.difficulty() == null) {
            throw invalid(location, "missing difficulty");
        }

        if (!regionNames.containsKey(location.region())) {
            throw invalid(location, "unknown region " + location.region());
        }

        if (!isInsideMap(location.x(), location.y())) {
            throw invalid(location, "outside the map");
        }

        if (isBlank(location.period())) {
            throw invalid(location, "missing period");
        }

        requireText(location, location.description(), MAX_DESCRIPTION_LENGTH, "description");
        requireText(location, location.explanation(), MAX_EXPLANATION_LENGTH, "explanation");

        // The description is shown during the question, so it must not contain the answer.
        if ((" " + normalize(location.description()) + " ")
                .contains(" " + normalize(location.name()) + " ")) {
            throw invalid(location, "the description gives away the name");
        }

        if (location.references().isEmpty()) {
            throw invalid(location, "needs at least one reference");
        }

        for (WhereInMiddleEarthLocation.Reference reference : location.references()) {
            if (isBlank(reference.source()) || isBlank(reference.supports())
                    || reference.url() == null || !reference.url().startsWith("https://")) {
                throw invalid(location, "incomplete reference");
            }
        }
    }

    private static void requireText(
            WhereInMiddleEarthLocation location, String text, int maxLength, String field) {
        if (isBlank(text) || !text.equals(text.strip())) {
            throw invalid(location, "blank or untrimmed " + field);
        }

        if (text.length() > maxLength) {
            throw invalid(location, field + " longer than " + maxLength + " characters");
        }

        if (!text.endsWith(".")) {
            throw invalid(location, field + " does not end with a period");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static IllegalArgumentException invalid(
            WhereInMiddleEarthLocation location, String problem) {
        return new IllegalArgumentException(
                "Where in Middle-earth place " + location.id() + ": " + problem);
    }

    private static LocationFile loadFromClasspath() {
        JsonMapper mapper = JsonMapper.builder().build();

        try (InputStream stream = new ClassPathResource(RESOURCE).getInputStream()) {
            return mapper.readValue(stream, LocationFile.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read " + RESOURCE, exception);
        }
    }
}
