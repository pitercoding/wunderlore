package com.wunderlore.game.whereinmiddleearth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthLocationBank.Region;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// The real curated content, checked against the map it is drawn on. The geometry file is shared
// with the frontend (contracts/where-in-middle-earth/map-geometry.json): the frontend draws the
// land from the same polygon, so a place inside it is a place on dry land of the SVG.
class WhereInMiddleEarthLocationBankTest {

    // Maven runs the tests from the backend directory.
    private static final Path GEOMETRY =
            Path.of("..", "contracts", "where-in-middle-earth", "map-geometry.json");

    private static final Path REFERENCES =
            Path.of("src", "main", "resources", "where-in-middle-earth", "REFERENCES.md");

    private static final List<Region> REGIONS = List.of(new Region("test-region", "Test Region"));

    private final WhereInMiddleEarthLocationBank bank = new WhereInMiddleEarthLocationBank();

    private List<WhereInMiddleEarthLocation> all() {
        List<WhereInMiddleEarthLocation> all = new ArrayList<>();

        for (WhereInMiddleEarthDifficulty difficulty : WhereInMiddleEarthDifficulty.values()) {
            all.addAll(bank.find(difficulty));
        }

        return all;
    }

    private static JsonNode geometry() throws IOException {
        return JsonMapper.builder().build().readTree(Files.readString(GEOMETRY));
    }

    private static List<double[]> polygon(JsonNode points) {
        List<double[]> polygon = new ArrayList<>();

        for (JsonNode point : points) {
            polygon.add(new double[] {point.get(0).asDouble(), point.get(1).asDouble()});
        }

        return polygon;
    }

    // Even-odd ray casting.
    private static boolean contains(List<double[]> polygon, double x, double y) {
        boolean inside = false;

        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            double[] a = polygon.get(i);
            double[] b = polygon.get(j);

            if ((a[1] > y) != (b[1] > y) && x < (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1]) + a[0]) {
                inside = !inside;
            }
        }

        return inside;
    }

    private static double distanceToEdges(List<double[]> polygon, double x, double y) {
        double best = Double.MAX_VALUE;

        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            double[] a = polygon.get(i);
            double[] b = polygon.get(j);
            double dx = b[0] - a[0];
            double dy = b[1] - a[1];
            double t = Math.max(0, Math.min(1, ((x - a[0]) * dx + (y - a[1]) * dy) / (dx * dx + dy * dy)));

            best = Math.min(best, Math.hypot(x - a[0] - t * dx, y - a[1] - t * dy));
        }

        return best;
    }

    @Test
    void shouldHoldFortyFivePlacesFifteenPerDifficulty() {
        assertThat(all()).hasSize(45);

        for (WhereInMiddleEarthDifficulty difficulty : WhereInMiddleEarthDifficulty.values()) {
            assertThat(bank.count(difficulty)).isEqualTo(15);
            assertThat(bank.find(difficulty))
                    .allSatisfy(location -> assertThat(location.difficulty()).isEqualTo(difficulty));
        }
    }

    @Test
    void shouldHaveUniqueIdsAndNames() {
        List<String> ids = all().stream().map(WhereInMiddleEarthLocation::id).toList();
        List<String> names = all().stream()
                .map(location -> WhereInMiddleEarthLocationBank.normalize(location.name()))
                .toList();

        assertThat(new HashSet<>(ids)).hasSameSizeAs(ids);
        assertThat(new HashSet<>(names)).hasSameSizeAs(names);
    }

    @Test
    void shouldKeepEveryPlaceInsideTheMap() {
        assertThat(all()).allSatisfy(location -> assertThat(
                WhereInMiddleEarthLocationBank.isInsideMap(location.x(), location.y()))
                .as(location.id()).isTrue());
    }

    @Test
    void shouldDescribeEveryPlaceAndSourceIt() {
        assertThat(all()).allSatisfy(location -> {
            assertThat(location.description()).as(location.id()).isNotBlank();
            assertThat(location.explanation()).as(location.id()).isNotBlank();
            assertThat(location.period()).as(location.id()).isNotBlank();
            assertThat(location.references()).as(location.id()).isNotEmpty();
            assertThat(location.references()).allSatisfy(reference -> {
                assertThat(reference.url()).startsWith("https://tolkiengateway.net/wiki/");
                assertThat(reference.source()).isNotBlank();
                assertThat(reference.supports()).isNotBlank();
            });
        });
    }

    @Test
    void shouldKeepPlacesOfOneDifficultyApartEnoughToScoreThemSeparately() {
        for (WhereInMiddleEarthDifficulty difficulty : WhereInMiddleEarthDifficulty.values()) {
            List<WhereInMiddleEarthLocation> pool = bank.find(difficulty);

            for (int i = 0; i < pool.size(); i++) {
                for (int j = i + 1; j < pool.size(); j++) {
                    double distance = WhereInMiddleEarthLocationBank.distance(
                            pool.get(i).x(), pool.get(i).y(), pool.get(j).x(), pool.get(j).y());

                    assertThat(distance)
                            .as(pool.get(i).id() + " and " + pool.get(j).id())
                            .isGreaterThan(2 * WhereInMiddleEarthAccuracy.EXCELLENT_RADIUS);
                }
            }
        }
    }

    @Test
    void shouldUseTheCoordinateSystemOfTheSvgViewBox() throws IOException {
        JsonNode viewBox = geometry().get("viewBox");

        assertThat(viewBox.get("width").asDouble()).isEqualTo(WhereInMiddleEarthLocationBank.MAP_WIDTH);
        assertThat(viewBox.get("height").asDouble()).isEqualTo(WhereInMiddleEarthLocationBank.MAP_HEIGHT);
    }

    @Test
    void shouldPlaceEveryPlaceOnDryLandOfTheMap() throws IOException {
        List<double[]> land = polygon(geometry().get("land"));

        assertThat(all()).allSatisfy(location -> {
            assertThat(contains(land, location.x(), location.y()))
                    .as(location.id() + " is on land").isTrue();
            // A few units from the shore, so that drawing the coast with curves cannot flood it.
            assertThat(distanceToEdges(land, location.x(), location.y()))
                    .as(location.id() + " is not on the shoreline").isGreaterThanOrEqualTo(4.0);
        });
    }

    @Test
    void shouldPlaceEveryPlaceInsideItsRegionOfTheMap() throws IOException {
        JsonNode regions = geometry().get("regions");

        assertThat(all()).allSatisfy(location -> {
            JsonNode region = null;

            for (JsonNode candidate : regions) {
                if (candidate.get("id").asString().equals(location.region())) {
                    region = candidate;
                }
            }

            assertThat(region).as(location.id() + " has a region on the map").isNotNull();
            assertThat(contains(polygon(region.get("polygon")), location.x(), location.y()))
                    .as(location.id() + " is inside " + location.region()).isTrue();
        });
    }

    @Test
    void shouldNameTheSameRegionsAsTheMap() throws IOException {
        List<String> onMap = new ArrayList<>();

        for (JsonNode region : geometry().get("regions")) {
            onMap.add(region.get("id").asString() + "=" + region.get("name").asString());
        }

        List<String> inBank = bank.regions().stream()
                .map(region -> region.id() + "=" + region.name())
                .toList();

        assertThat(inBank).containsExactlyInAnyOrderElementsOf(onMap);
    }

    @Test
    void shouldNotWriteTheNameOfAnyPlaceOnTheMap() throws IOException {
        Set<String> names = new HashSet<>();

        for (WhereInMiddleEarthLocation location : all()) {
            names.add(WhereInMiddleEarthLocationBank.normalize(location.name()));
            names.add(WhereInMiddleEarthLocationBank.normalize(location.name().replaceFirst("^The ", "")));
        }

        for (JsonNode label : geometry().get("labels")) {
            String text = WhereInMiddleEarthLocationBank.normalize(label.asString());

            assertThat(names).as("map label " + text).doesNotContain(text);
            assertThat(names).allSatisfy(name ->
                    assertThat(" " + text + " ").as(label.asString() + " vs " + name)
                            .doesNotContain(" " + name + " "));
        }
    }

    @Test
    void shouldListEveryPlaceInTheReferencesRecord() throws IOException {
        String record = Files.readString(REFERENCES);

        assertThat(all()).allSatisfy(location ->
                assertThat(record).as(location.id()).contains("`" + location.id() + "`"));
    }

    @Test
    void shouldRefuseAMalformedPlaceBeforeTheApplicationStarts() {
        List<WhereInMiddleEarthLocation> good =
                WhereInMiddleEarthTestData.locations(WhereInMiddleEarthDifficulty.EASY);

        assertThatThrownBy(() -> bankOf(good)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MEDIUM has 0 places");
    }

    private WhereInMiddleEarthLocationBank bankOf(List<WhereInMiddleEarthLocation> locations) {
        return new WhereInMiddleEarthLocationBank(REGIONS, locations);
    }

    private WhereInMiddleEarthLocationBank bankWith(WhereInMiddleEarthLocation replaced, int at) {
        List<WhereInMiddleEarthLocation> all = new ArrayList<>();

        for (WhereInMiddleEarthDifficulty difficulty : WhereInMiddleEarthDifficulty.values()) {
            for (WhereInMiddleEarthLocation location : WhereInMiddleEarthTestData.locations(difficulty)) {
                all.add(new WhereInMiddleEarthLocation(
                        difficulty.name().toLowerCase() + "-" + location.id(),
                        difficulty + " " + location.name(),
                        difficulty, location.region(), location.x(), location.y(),
                        location.period(), location.description(), location.explanation(),
                        location.references()));
            }
        }

        if (replaced != null) {
            all.set(at, replaced);
        }

        return bankOf(all);
    }

    private static WhereInMiddleEarthLocation with(
            WhereInMiddleEarthLocation base, String id, String name, String region, double x,
            double y, String description) {
        return new WhereInMiddleEarthLocation(id, name, base.difficulty(), region, x, y,
                base.period(), description, base.explanation(), base.references());
    }

    @Test
    void shouldAcceptAWellFormedBank() {
        assertThat(bankWith(null, 0).count(WhereInMiddleEarthDifficulty.HARD)).isEqualTo(10);
    }

    @Test
    void shouldRefuseCoordinatesOutsideTheMap() {
        WhereInMiddleEarthLocation base = WhereInMiddleEarthTestData.location(0, WhereInMiddleEarthDifficulty.EASY);

        assertThatThrownBy(() -> bankWith(with(base, "easy-x", "Easy X", "test-region", 1000.5, 10,
                "A spot."), 0))
                .hasMessageContaining("outside the map");
        assertThatThrownBy(() -> bankWith(with(base, "easy-x", "Easy X", "test-region", 10, -1,
                "A spot."), 0))
                .hasMessageContaining("outside the map");
        assertThatThrownBy(() -> bankWith(with(base, "easy-x", "Easy X", "test-region", Double.NaN, 10,
                "A spot."), 0))
                .hasMessageContaining("outside the map");
    }

    @Test
    void shouldRefuseAnUnknownRegionADuplicateIdAndAGivenAwayName() {
        WhereInMiddleEarthLocation base = WhereInMiddleEarthTestData.location(0, WhereInMiddleEarthDifficulty.EASY);

        assertThatThrownBy(() -> bankWith(with(base, "easy-x", "Easy X", "nowhere", 10, 10, "A spot."), 0))
                .hasMessageContaining("unknown region");
        assertThatThrownBy(() -> bankWith(with(base, "easy-place-1", "Easy X", "test-region", 10, 10,
                "A spot."), 0))
                .hasMessageContaining("duplicate id");
        assertThatThrownBy(() -> bankWith(with(base, "easy-x", "Easy X", "test-region", 10, 10,
                "Easy X is right here."), 0))
                .hasMessageContaining("gives away the name");
    }

    @Test
    void shouldRefusePlacesTooCloseToScoreSeparately() {
        WhereInMiddleEarthLocation base = WhereInMiddleEarthTestData.location(0, WhereInMiddleEarthDifficulty.EASY);

        // The EASY pool has places at x = 50, 140, 230...: replacing the first with one at x = 125
        // leaves it 15 units from the second, inside the "excellent" disc of both.
        assertThatThrownBy(() -> bankWith(
                with(base, "easy-x", "Easy X", "test-region", 125.0, 200.0, "A spot."), 0))
                .hasMessageContaining("too close");
    }
}
