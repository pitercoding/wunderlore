package com.wunderlore.ranking;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.wunderlore.ranking.RankingException.Reason;

@WebMvcTest(RankingController.class)
class RankingControllerTest {

    private static final String ENTRIES = "/api/ranking/entries";

    private static final Instant PLAYED_AT = Instant.parse("2026-03-01T10:15:30Z");

    // Required by LoreUnavailableAdvice, which the web slice loads with the controller.
    @TestConfiguration
    static class ClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(PLAYED_AT, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RankingService service;

    private static RankingEntry entry(String nickname) {
        return new RankingEntry(GameType.WHO_SAID_IT, "game-1", nickname, new FinishedGame(70, 100, PLAYED_AT, RankingDivision.STANDARD));
    }

    private void publish(String body) throws Exception {
        mockMvc.perform(post(ENTRIES).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void shouldAnswer201WithTheEntryWhenItIsCreated() throws Exception {
        when(service.publish("who-said-it", "game-1", "Frodo"))
                .thenReturn(new RankingService.Published(entry("Frodo"), true));

        mockMvc.perform(post(ENTRIES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"who-said-it\",\"gameId\":\"game-1\",\"nickname\":\"Frodo\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameType").value("who-said-it"))
                .andExpect(jsonPath("$.nickname").value("Frodo"))
                .andExpect(jsonPath("$.percentage").value(70.0))
                .andExpect(jsonPath("$.playedAt").value("2026-03-01T10:15:30Z"))
                .andExpect(jsonPath("$.alreadyPublished").value(false))
                // Nothing internal leaks: no id, no raw score, no game id.
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.score").doesNotExist())
                .andExpect(jsonPath("$.gameId").doesNotExist());
    }

    @Test
    void shouldAnswer200WhenTheGameWasAlreadyPublished() throws Exception {
        when(service.publish("who-said-it", "game-1", "Other"))
                .thenReturn(new RankingService.Published(entry("Frodo"), false));

        mockMvc.perform(post(ENTRIES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"who-said-it\",\"gameId\":\"game-1\",\"nickname\":\"Other\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("Frodo"))
                .andExpect(jsonPath("$.alreadyPublished").value(true));
    }

    @Test
    void shouldIgnoreAScoreSentByTheClient() throws Exception {
        when(service.publish(any(), any(), any()))
                .thenReturn(new RankingService.Published(entry("Frodo"), true));

        publish("{\"gameType\":\"who-said-it\",\"gameId\":\"game-1\",\"nickname\":\"Frodo\","
                + "\"score\":100,\"maxScore\":100,\"percentage\":100}");

        // The service is only ever given the game and the nickname.
        verify(service).publish("who-said-it", "game-1", "Frodo");
    }

    @Test
    void shouldRejectMalformedBodies() throws Exception {
        for (String body : List.of(
                "{}",
                "{\"gameType\":\"who-said-it\",\"gameId\":\"g\"}",
                "{\"gameType\":\"\",\"gameId\":\"g\",\"nickname\":\"Frodo\"}",
                "{\"gameType\":\"who-said-it\",\"gameId\":\"" + "x".repeat(65) + "\",\"nickname\":\"Frodo\"}",
                "{\"gameType\":\"who-said-it\",\"gameId\":\"g\",\"nickname\":\"" + "x".repeat(65) + "\"}",
                "not json")) {
            mockMvc.perform(post(ENTRIES).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void shouldMapServiceFailuresToTheirStatuses() throws Exception {
        String body = "{\"gameType\":\"who-said-it\",\"gameId\":\"g\",\"nickname\":\"Frodo\"}";

        for (var expectation : List.of(
                new Object[] {Reason.INVALID_NICKNAME, 400},
                new Object[] {Reason.RESERVED_NICKNAME, 400},
                new Object[] {Reason.GAME_NOT_FOUND, 404},
                new Object[] {Reason.GAME_NOT_FINISHED, 409},
                new Object[] {Reason.UNAVAILABLE, 503})) {
            Reason reason = (Reason) expectation[0];

            doThrow(new RankingException(reason)).when(service).publish(any(), any(), any());

            mockMvc.perform(post(ENTRIES).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().is((int) expectation[1]))
                    .andExpect(jsonPath("$.detail").value(reason.detail()));
        }
    }

    @Test
    void shouldReturnTheRankingOfOneDivisionOfOneGame() throws Exception {
        RankingEntryRepository.Row row = new RankingEntryRepository.Row() {
            public long getPosition() { return 1; }
            public String getNickname() { return "Frodo"; }
            public String getGameType() { return "WHERE_IN_MIDDLE_EARTH"; }
            public String getDivision() { return "MEDIUM"; }
            public BigDecimal getPercentage() { return new BigDecimal("93.500000"); }
            public Instant getPlayedAt() { return PLAYED_AT; }
        };

        when(service.top("where-in-middle-earth", "medium")).thenReturn(List.of(row));

        mockMvc.perform(get("/api/ranking").param("game", "where-in-middle-earth").param("division", "medium"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.game").value("where-in-middle-earth"))
                .andExpect(jsonPath("$.division").value("medium"))
                .andExpect(jsonPath("$.entries", hasSize(1)))
                .andExpect(jsonPath("$.entries[0].position").value(1))
                .andExpect(jsonPath("$.entries[0].nickname").value("Frodo"))
                .andExpect(jsonPath("$.entries[0].gameType").value("where-in-middle-earth"))
                .andExpect(jsonPath("$.entries[0].division").value("medium"))
                .andExpect(jsonPath("$.entries[0].percentage").value(93.5))
                .andExpect(jsonPath("$.entries[0].playedAt").value("2026-03-01T10:15:30Z"));
    }

    @Test
    void shouldAnswer200WithAnEmptyListForADivisionWithoutResults() throws Exception {
        when(service.top("who-said-it", "standard")).thenReturn(List.of());

        mockMvc.perform(get("/api/ranking").param("game", "who-said-it").param("division", "standard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.game").value("who-said-it"))
                .andExpect(jsonPath("$.division").value("standard"))
                .andExpect(jsonPath("$.entries", hasSize(0)));
    }

    @Test
    void shouldPassTheGameAndDivisionOfEveryValidCombinationToTheService() throws Exception {
        for (GameType type : GameType.values()) {
            for (RankingDivision division : RankingDivision.of(type)) {
                when(service.top(type.slug(), division.slug())).thenReturn(List.of());

                mockMvc.perform(get("/api/ranking").param("game", type.slug()).param("division", division.slug()))
                        .andExpect(status().isOk());
                verify(service).top(eq(type.slug()), eq(division.slug()));
            }
        }
    }

    @Test
    void shouldAnswer400ForAnUnknownGameAnUnknownDivisionOrAnInvalidCombination() throws Exception {
        when(service.top("nonsense", "easy")).thenThrow(new RankingException(Reason.INVALID_GAME));
        when(service.top("lore-challenge", "nonsense")).thenThrow(new RankingException(Reason.INVALID_DIVISION));
        when(service.top("who-said-it", "easy")).thenThrow(new RankingException(Reason.INVALID_DIVISION));
        when(service.top("lore-challenge", "standard")).thenThrow(new RankingException(Reason.INVALID_DIVISION));

        mockMvc.perform(get("/api/ranking").param("game", "nonsense").param("division", "easy"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(Reason.INVALID_GAME.detail()));
        mockMvc.perform(get("/api/ranking").param("game", "lore-challenge").param("division", "nonsense"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(Reason.INVALID_DIVISION.detail()));
        mockMvc.perform(get("/api/ranking").param("game", "who-said-it").param("division", "easy"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(Reason.INVALID_DIVISION.detail()));
        mockMvc.perform(get("/api/ranking").param("game", "lore-challenge").param("division", "standard"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(Reason.INVALID_DIVISION.detail()));
    }

    @Test
    void shouldNotAnswerWithAGlobalRankingWhenTheFiltersAreMissing() throws Exception {
        when(service.top(null, null)).thenThrow(new RankingException(Reason.INVALID_GAME));
        when(service.top("who-am-i", null)).thenThrow(new RankingException(Reason.INVALID_DIVISION));

        mockMvc.perform(get("/api/ranking")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/ranking").param("game", "who-am-i")).andExpect(status().isBadRequest());
    }

    @Test
    void shouldAnswer503WhenTheLeaderboardIsUnavailable() throws Exception {
        when(service.top("lore-challenge", "easy")).thenThrow(new RankingException(Reason.UNAVAILABLE));

        mockMvc.perform(get("/api/ranking").param("game", "lore-challenge").param("division", "easy"))
                .andExpect(status().isServiceUnavailable());
    }

    private static RankingEntryRepository.Row row(long position, String nickname, GameType game,
            RankingDivision division, String percentage) {
        return new RankingEntryRepository.Row() {
            public long getPosition() { return position; }
            public String getNickname() { return nickname; }
            public String getGameType() { return game.name(); }
            public String getDivision() { return division.name(); }
            public BigDecimal getPercentage() { return new BigDecimal(percentage); }
            public Instant getPlayedAt() { return PLAYED_AT; }
        };
    }

    @Test
    void shouldAnswerTheOverviewWithOneGroupPerGameAndDivision() throws Exception {
        when(service.overview()).thenReturn(List.of(
                new RankingService.OverviewGroup(GameType.LORE_CHALLENGE, RankingDivision.EASY,
                        List.of(row(1, "Aria", GameType.LORE_CHALLENGE, RankingDivision.EASY, "95.000000"))),
                new RankingService.OverviewGroup(GameType.LORE_CHALLENGE, RankingDivision.MEDIUM, List.of()),
                new RankingService.OverviewGroup(GameType.WHO_SAID_IT, RankingDivision.STANDARD, List.of())));

        mockMvc.perform(get("/api/ranking/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups", hasSize(3)))
                .andExpect(jsonPath("$.groups[0].game").value("lore-challenge"))
                .andExpect(jsonPath("$.groups[0].division").value("easy"))
                .andExpect(jsonPath("$.groups[0].entries", hasSize(1)))
                .andExpect(jsonPath("$.groups[0].entries[0].position").value(1))
                .andExpect(jsonPath("$.groups[0].entries[0].nickname").value("Aria"))
                .andExpect(jsonPath("$.groups[0].entries[0].gameType").value("lore-challenge"))
                .andExpect(jsonPath("$.groups[0].entries[0].division").value("easy"))
                .andExpect(jsonPath("$.groups[0].entries[0].percentage").value(95.0))
                .andExpect(jsonPath("$.groups[0].entries[0].playedAt").value("2026-03-01T10:15:30Z"))
                .andExpect(jsonPath("$.groups[1].game").value("lore-challenge"))
                .andExpect(jsonPath("$.groups[1].division").value("medium"))
                .andExpect(jsonPath("$.groups[1].entries", hasSize(0)))
                .andExpect(jsonPath("$.groups[2].game").value("who-said-it"))
                .andExpect(jsonPath("$.groups[2].division").value("standard"))
                .andExpect(jsonPath("$.groups[2].entries", hasSize(0)));
    }

    @Test
    void shouldAnswer503ForTheOverviewWhenTheLeaderboardIsUnavailable() throws Exception {
        when(service.overview()).thenThrow(new RankingException(Reason.UNAVAILABLE));

        mockMvc.perform(get("/api/ranking/overview"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(Reason.UNAVAILABLE.detail()));
    }
}
