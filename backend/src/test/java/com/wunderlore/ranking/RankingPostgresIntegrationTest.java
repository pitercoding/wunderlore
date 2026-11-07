package com.wunderlore.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;
import com.wunderlore.client.theone.TheOneApiClient;
import com.wunderlore.client.theone.dto.ExternalCharacter;
import com.wunderlore.client.theone.dto.ExternalCharacterResponse;
import com.wunderlore.client.theone.dto.ExternalQuote;
import com.wunderlore.client.theone.dto.ExternalQuoteResponse;

// The whole application against a real PostgreSQL (not H2, whose SQL differs): Flyway creates the
// table, Hibernate validates the mapping, RANK() and the unique constraint are the database's own.
// Skipped when no Docker daemon is available. The four games are played for real through HTTP;
// only The One API, which Who Said It? needs, is replaced.
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "wunderlore.persistence.enabled=true",
        "wunderlore.lore.failure-cooldown=PT0S",
        "wunderlore.rate-limit.ranking-publish.enabled=false",
        "wunderlore.rate-limit.who-said-it-match-creation.enabled=false",
        "wunderlore.rate-limit.who-am-i-game-creation.enabled=false",
        "wunderlore.rate-limit.where-in-middle-earth-game-creation.enabled=false",
        "wunderlore.rate-limit.match-creation.enabled=false"
})
@AutoConfigureMockMvc
class RankingPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoBean
    private TheOneApiClient theOneApiClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RankingEntryRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private GamePlayer player;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        player = new GamePlayer(mockMvc);

        List<ExternalQuote> quotes = new ArrayList<>();
        List<ExternalCharacter> characters = new ArrayList<>();

        for (int i = 0; i < 30; i++) {
            quotes.add(new ExternalQuote("q" + i, "Dialog number " + i, "movie", "char-" + (i % 12), "q" + i));
        }

        for (int i = 0; i < 12; i++) {
            characters.add(new ExternalCharacter("char-" + i, "Character " + i));
        }

        org.mockito.Mockito.when(theOneApiClient.getQuotes(1, 100))
                .thenReturn(new ExternalQuoteResponse(quotes, quotes.size(), 100, null, 1, 1));
        org.mockito.Mockito.when(theOneApiClient.getCharacters(1, 100))
                .thenReturn(new ExternalCharacterResponse(characters, characters.size(), 100, null, 1, 1));
    }

    private String publish(GameType type, String gameId, String nickname, int expectedStatus) throws Exception {
        return mockMvc.perform(post("/api/ranking/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"" + type.slug() + "\",\"gameId\":\"" + gameId
                                + "\",\"nickname\":\"" + nickname + "\"}"))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    private String leaderboard(String game) throws Exception {
        var request = get("/api/ranking");

        if (game != null) {
            request = request.param("game", game);
        }

        return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private void insert(GameType type, String gameId, String nickname, int score, int max, String playedAt) {
        repository.saveAndFlush(new RankingEntry(
                type, gameId, nickname, new FinishedGame(score, max, Instant.parse(playedAt))));
    }

    @Test
    void shouldPublishAFinishedGameOfEachOfTheFourGamesWithTheScoreTheGameReports() throws Exception {
        for (GameType type : GameType.values()) {
            GamePlayer.Played played = player.play(type);

            String body = publish(type, played.gameId(), "Player " + type.ordinal(), 201);

            BigDecimal expected = BigDecimal.valueOf(played.score() * 100.0 / played.maxScore());

            assertThat(new BigDecimal(JsonPath.read(body, "$.percentage").toString()))
                    .isCloseTo(expected, org.assertj.core.data.Offset.offset(new BigDecimal("0.000001")));
            assertThat((String) JsonPath.read(body, "$.gameType")).isEqualTo(type.slug());

            var stored = repository.findByGameTypeAndGameId(type, played.gameId()).orElseThrow();

            assertThat(stored.getScore()).isEqualTo(played.score());
            assertThat(stored.getMaxScore()).isEqualTo(played.maxScore());
        }

        assertThat(repository.count()).isEqualTo(4);
    }

    @Test
    void shouldRefusePublishingGamesThatDoNotExistOrAreNotFinished() throws Exception {
        publish(GameType.WHO_SAID_IT, "00000000-0000-4000-8000-000000000000", "Frodo", 404);

        for (GameType type : GameType.values()) {
            String unfinished = player.startUnfinished(type);

            publish(type, unfinished, "Frodo", 409);
        }

        // A finished game of one kind cannot be published as another.
        GamePlayer.Played played = player.play(GameType.WHO_SAID_IT);

        publish(GameType.WHO_AM_I, played.gameId(), "Frodo", 404);
        assertThat(repository.count()).isZero();
    }

    @Test
    void shouldRejectInvalidNicknamesAndKeepThePartyPublishable() throws Exception {
        GamePlayer.Played played = player.play(GameType.WHO_SAID_IT);

        publish(GameType.WHO_SAID_IT, played.gameId(), "ab", 400);
        publish(GameType.WHO_SAID_IT, played.gameId(), "Admin", 400);
        publish(GameType.WHO_SAID_IT, played.gameId(), "bad!name", 400);
        assertThat(repository.count()).isZero();

        publish(GameType.WHO_SAID_IT, played.gameId(), "Bilbo", 201);
    }

    @Test
    void shouldIgnoreAScoreSentByTheClient() throws Exception {
        GamePlayer.Played played = player.play(GameType.WHO_SAID_IT);

        mockMvc.perform(post("/api/ranking/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"who-said-it\",\"gameId\":\"" + played.gameId()
                                + "\",\"nickname\":\"Cheater\",\"score\":100,\"maxScore\":100,\"percentage\":100}"))
                .andExpect(status().isCreated());

        var stored = repository.findByGameTypeAndGameId(GameType.WHO_SAID_IT, played.gameId()).orElseThrow();

        assertThat(stored.getScore()).isEqualTo(played.score());
        assertThat(stored.getMaxScore()).isEqualTo(100);
    }

    @Test
    void shouldPublishOnlyOnceAndAnswerRepeatsWithTheOriginalEntry() throws Exception {
        GamePlayer.Played played = player.play(GameType.WHO_SAID_IT);

        publish(GameType.WHO_SAID_IT, played.gameId(), "First", 201);

        String repeat = publish(GameType.WHO_SAID_IT, played.gameId(), "Second", 200);

        assertThat((String) JsonPath.read(repeat, "$.nickname")).isEqualTo("First");
        assertThat((Boolean) JsonPath.read(repeat, "$.alreadyPublished")).isTrue();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void shouldCreateExactlyOneEntryWhenTheSameGameIsPublishedConcurrently() throws Exception {
        GamePlayer.Played played = player.play(GameType.WHO_SAID_IT);
        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger existing = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                String nickname = "Racer " + i;

                futures.add(pool.submit(() -> {
                    start.await();

                    int code = mockMvc.perform(post("/api/ranking/entries")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"gameType\":\"who-said-it\",\"gameId\":\"" + played.gameId()
                                            + "\",\"nickname\":\"" + nickname + "\"}"))
                            .andReturn().getResponse().getStatus();

                    if (code == 201) {
                        created.incrementAndGet();
                    } else if (code == 200) {
                        existing.incrementAndGet();
                    }

                    return null;
                }));
            }

            start.countDown();

            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(repository.count()).isEqualTo(1);
        assertThat(created.get()).isEqualTo(1);
        assertThat(existing.get()).isEqualTo(threads - 1);
    }

    @Test
    void shouldEnforceTheUniqueConstraintInTheDatabaseItself() {
        insert(GameType.WHO_SAID_IT, "same", "First", 50, 100, "2026-01-01T00:00:00Z");

        // Bypassing the service entirely, the schema still refuses a second entry for the game...
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> insert(GameType.WHO_SAID_IT, "same", "Second", 60, 100, "2026-01-01T00:00:00Z"));

        // ...while the same id in another game is a different game.
        insert(GameType.WHO_AM_I, "same", "Third", 60, 100, "2026-01-01T00:00:00Z");
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void shouldOrderByPercentageThenEarlierGameAndShareThePositionOnExactTies() throws Exception {
        insert(GameType.WHO_SAID_IT, "a", "Low", 30, 100, "2026-01-01T00:00:00Z");
        insert(GameType.WHO_SAID_IT, "b", "TieLater", 80, 100, "2026-01-03T00:00:00Z");
        insert(GameType.WHO_AM_I, "c", "Best", 100, 100, "2026-01-05T00:00:00Z");
        insert(GameType.WHO_SAID_IT, "d", "TieEarlier", 80, 100, "2026-01-02T00:00:00Z");
        // 8/10 is exactly 80% as well, from another game with another maximum.
        insert(GameType.LORE_CHALLENGE, "e", "TieOtherMax", 240, 300, "2026-01-04T00:00:00Z");

        String body = leaderboard(null);

        List<String> nicknames = JsonPath.read(body, "$.entries[*].nickname");
        List<Integer> positions = JsonPath.read(body, "$.entries[*].position");

        assertThat(nicknames).containsExactly("Best", "TieEarlier", "TieLater", "TieOtherMax", "Low");
        assertThat(positions).containsExactly(1, 2, 2, 2, 5);
    }

    @Test
    void shouldNeverOrderByTheRoundedPercentage() throws Exception {
        // 33.33% displayed for both, but 1000/3000 = 33.333333 is better than 3333/10000 = 33.33.
        insert(GameType.WHO_SAID_IT, "a", "Worse", 3333, 10000, "2026-01-01T00:00:00Z");
        insert(GameType.WHO_SAID_IT, "b", "Better", 1000, 3000, "2026-01-02T00:00:00Z");

        String body = leaderboard(null);

        assertThat((List<String>) JsonPath.read(body, "$.entries[*].nickname")).containsExactly("Better", "Worse");
        assertThat((List<Integer>) JsonPath.read(body, "$.entries[*].position")).containsExactly(1, 2);
    }

    @Test
    void shouldRecalculatePositionsInsideAFilteredGame() throws Exception {
        insert(GameType.WHO_AM_I, "a", "Top", 100, 100, "2026-01-01T00:00:00Z");
        insert(GameType.WHO_SAID_IT, "b", "Second", 90, 100, "2026-01-01T00:00:00Z");
        insert(GameType.WHO_SAID_IT, "c", "Third", 70, 100, "2026-01-01T00:00:00Z");

        String filtered = leaderboard("who-said-it");

        assertThat((List<String>) JsonPath.read(filtered, "$.entries[*].nickname")).containsExactly("Second", "Third");
        assertThat((List<Integer>) JsonPath.read(filtered, "$.entries[*].position")).containsExactly(1, 2);
        assertThat((List<String>) JsonPath.read(filtered, "$.entries[*].gameType"))
                .containsOnly("who-said-it");
        assertThat((String) JsonPath.read(filtered, "$.game")).isEqualTo("who-said-it");

        // Global positions are unaffected.
        assertThat((List<Integer>) JsonPath.read(leaderboard(null), "$.entries[*].position")).containsExactly(1, 2, 3);
    }

    @Test
    void shouldShowOnlyTheBestFiftyAndKeepTheCorrectRankAtTheCut() throws Exception {
        for (int i = 0; i < 60; i++) {
            // Scores 0..59 out of 100, with 49 and 48 tied below the cut line is not needed; the cut
            // splits a tie: 5 results share the 50th place.
            int score = i < 45 ? 100 - i : 55;

            insert(GameType.WHO_SAID_IT, "g" + i, "Player " + i, score, 100, "2026-01-01T00:00:00Z");
        }

        String body = leaderboard(null);
        List<Integer> positions = JsonPath.read(body, "$.entries[*].position");

        assertThat(positions).hasSize(50);
        assertThat(positions.get(0)).isEqualTo(1);
        assertThat(positions.get(44)).isEqualTo(45);
        // Every row at the 55% tie shares position 46, even though the list is cut inside the tie.
        assertThat(positions.subList(45, 50)).containsOnly(46);
    }

    @Test
    void shouldStoreAndReturnTimestampsInUtc() throws Exception {
        insert(GameType.WHO_SAID_IT, "a", "Frodo", 80, 100, "2026-03-01T23:30:00Z");

        String body = leaderboard(null);

        assertThat((String) JsonPath.read(body, "$.entries[0].playedAt")).isEqualTo("2026-03-01T23:30:00Z");

        // The column is timestamptz: the instant survives whatever zone the session uses.
        jdbc.execute("SET TIME ZONE 'America/Sao_Paulo'");
        assertThat(jdbc.queryForObject(
                "SELECT to_char(played_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS') FROM ranking_entries LIMIT 1",
                String.class)).isEqualTo("2026-03-01T23:30:00");
        assertThat(jdbc.queryForObject(
                "SELECT data_type FROM information_schema.columns WHERE table_name = 'ranking_entries' AND column_name = 'played_at'",
                String.class)).isEqualTo("timestamp with time zone");
    }

    @Test
    void shouldReturnAnEmptyLeaderboardWhenNothingWasPublished() throws Exception {
        assertThat((List<?>) JsonPath.read(leaderboard(null), "$.entries")).isEmpty();
    }

    @Test
    void shouldHaveTheIndexesThatServeTheOrderings() {
        List<String> indexes = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'ranking_entries'", String.class);

        assertThat(indexes).contains(
                "ranking_entries_global_order_idx", "ranking_entries_game_order_idx", "ranking_entries_game_unique");
    }
}
