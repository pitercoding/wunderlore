package com.wunderlore.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// V1 -> V2 on a real PostgreSQL, with rows that exist before the migration: the backfill, the
// check constraint and the new index. Flyway is used directly (no Spring context), so the schema
// is rebuilt from scratch for every test. Skipped when no Docker daemon is available.
@Testcontainers(disabledWithoutDocker = true)
class RankingDivisionMigrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private Connection connection;

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .target(target)
                .load();
    }

    @BeforeEach
    void freshSchemaAtV1() throws SQLException {
        Flyway clean = flyway("1");

        clean.clean();
        clean.migrate();

        connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @AfterEach
    void closeConnection() throws SQLException {
        connection.close();
    }

    // Inserts through plain SQL, so it works before the division column exists.
    private String insertV1(String gameType, int score, int maxScore) throws SQLException {
        String gameId = UUID.randomUUID().toString();

        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ranking_entries
                    (id, game_type, game_id, nickname, score, max_score, percentage, played_at)
                VALUES (?, ?, ?, 'Tester', ?, ?, ?, now())
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, gameType);
            statement.setString(3, gameId);
            statement.setInt(4, score);
            statement.setInt(5, maxScore);
            statement.setBigDecimal(6, java.math.BigDecimal.valueOf(score * 100.0 / maxScore));
            statement.executeUpdate();
        }

        return gameId;
    }

    private String divisionOf(String gameId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT division FROM ranking_entries WHERE game_id = ?")) {
            statement.setString(1, gameId);

            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();

                return rows.getString(1);
            }
        }
    }

    private void insertV2(String gameType, String division) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ranking_entries
                    (id, game_type, game_id, nickname, score, max_score, percentage, played_at, division)
                VALUES (?, ?, ?, 'Tester', 50, 100, 50, now(), ?)
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, gameType);
            statement.setString(3, UUID.randomUUID().toString());
            statement.setString(4, division);
            statement.executeUpdate();
        }
    }

    @Test
    void shouldBackfillLoreChallengeByMaxScore() throws SQLException {
        String easy = insertV1("LORE_CHALLENGE", 70, 100);
        String medium = insertV1("LORE_CHALLENGE", 120, 200);
        String hard = insertV1("LORE_CHALLENGE", 300, 300);

        flyway("2").migrate();

        assertThat(divisionOf(easy)).isEqualTo("EASY");
        assertThat(divisionOf(medium)).isEqualTo("MEDIUM");
        assertThat(divisionOf(hard)).isEqualTo("HARD");
    }

    @Test
    void shouldLeaveALoreChallengeRowWithAnUnexpectedMaxScoreNullInsteadOfFailing() throws SQLException {
        String odd = insertV1("LORE_CHALLENGE", 50, 150);

        flyway("2").migrate();

        assertThat(divisionOf(odd)).isNull();
    }

    @Test
    void shouldBackfillWhoSaidItAsStandard() throws SQLException {
        String perfect = insertV1("WHO_SAID_IT", 100, 100);
        String partial = insertV1("WHO_SAID_IT", 30, 100);

        flyway("2").migrate();

        assertThat(divisionOf(perfect)).isEqualTo("STANDARD");
        assertThat(divisionOf(partial)).isEqualTo("STANDARD");
    }

    @Test
    void shouldKeepNullForWhoAmIAndWhereInMiddleEarth() throws SQLException {
        // max_score is 1000 for every difficulty of both games, so nothing can be recovered.
        String whoAmI = insertV1("WHO_AM_I", 800, 1000);
        String where = insertV1("WHERE_IN_MIDDLE_EARTH", 400, 1000);

        flyway("2").migrate();

        assertThat(divisionOf(whoAmI)).isNull();
        assertThat(divisionOf(where)).isNull();
    }

    @Test
    void shouldMigrateAnEmptyTable() {
        assertThat(flyway("2").migrate().migrationsExecuted).isEqualTo(1);
    }

    @Test
    void shouldAcceptEveryValidCombinationAfterTheMigration() throws SQLException {
        flyway("2").migrate();

        for (GameType game : GameType.values()) {
            for (RankingDivision division : RankingDivision.of(game)) {
                insertV2(game.name(), division.name());
            }
        }
    }

    @Test
    void shouldRejectEveryInvalidCombinationInTheDatabase() throws SQLException {
        flyway("2").migrate();

        List<String> rejected = new ArrayList<>();

        for (GameType game : GameType.values()) {
            for (RankingDivision division : RankingDivision.values()) {
                if (division.isValidFor(game)) {
                    continue;
                }

                assertThatThrownBy(() -> insertV2(game.name(), division.name()))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("ranking_entries_division_check");
                rejected.add(game + "/" + division);
            }
        }

        assertThat(rejected).hasSize(6);
    }

    @Test
    void shouldRejectAnUnknownDivisionValue() throws SQLException {
        flyway("2").migrate();

        assertThatThrownBy(() -> insertV2("LORE_CHALLENGE", "EXPERT"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ranking_entries_division_check");
    }

    @Test
    void shouldStillAcceptRowsWithoutADivisionSoThePreviousApplicationVersionKeepsWorking()
            throws SQLException {
        flyway("2").migrate();

        // The shape of the INSERT that version 1 of the application sends: no division column.
        String gameId = insertV1("LORE_CHALLENGE", 10, 100);

        assertThat(divisionOf(gameId)).isNull();

        insertV2("WHO_AM_I", null);
    }

    @Test
    void shouldCreateTheDivisionIndexAndKeepTheOnesFromV1() throws SQLException {
        flyway("2").migrate();

        List<String> definitions = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'ranking_entries'")) {
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    definitions.add(rows.getString(1) + " " + rows.getString(2));
                }
            }
        }

        assertThat(definitions)
                .anyMatch(definition -> definition.startsWith("ranking_entries_division_order_idx")
                        && definition.contains("(game_type, division, percentage DESC, played_at, id)"))
                .anyMatch(definition -> definition.startsWith("ranking_entries_global_order_idx"))
                .anyMatch(definition -> definition.startsWith("ranking_entries_game_order_idx"));
    }

    @Test
    void shouldKeepTheUniqueConstraintOnGameTypeAndGameId() throws SQLException {
        flyway("2").migrate();

        String gameId = UUID.randomUUID().toString();

        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ranking_entries
                    (id, game_type, game_id, nickname, score, max_score, percentage, played_at, division)
                VALUES (?, 'WHO_SAID_IT', ?, 'Tester', 50, 100, 50, now(), 'STANDARD')
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, gameId);
            statement.executeUpdate();
            statement.setObject(1, UUID.randomUUID());

            assertThatThrownBy(statement::executeUpdate)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("ranking_entries_game_unique");
        }
    }
}
