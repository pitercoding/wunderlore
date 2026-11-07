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
        return new RankingEntry(GameType.WHO_SAID_IT, "game-1", nickname, new FinishedGame(70, 100, PLAYED_AT));
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
    void shouldReturnTheGlobalLeaderboardByDefault() throws Exception {
        RankingEntryRepository.Row row = new RankingEntryRepository.Row() {
            public long getPosition() { return 1; }
            public String getNickname() { return "Frodo"; }
            public String getGameType() { return "WHERE_IN_MIDDLE_EARTH"; }
            public BigDecimal getPercentage() { return new BigDecimal("93.500000"); }
            public Instant getPlayedAt() { return PLAYED_AT; }
        };

        when(service.top(null)).thenReturn(List.of(row));

        mockMvc.perform(get("/api/ranking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.game").value((Object) null))
                .andExpect(jsonPath("$.entries", hasSize(1)))
                .andExpect(jsonPath("$.entries[0].position").value(1))
                .andExpect(jsonPath("$.entries[0].nickname").value("Frodo"))
                .andExpect(jsonPath("$.entries[0].gameType").value("where-in-middle-earth"))
                .andExpect(jsonPath("$.entries[0].percentage").value(93.5))
                .andExpect(jsonPath("$.entries[0].playedAt").value("2026-03-01T10:15:30Z"));
    }

    @Test
    void shouldPassTheGameFilterAndMapItsFailures() throws Exception {
        when(service.top("who-am-i")).thenReturn(List.of());
        when(service.top("nonsense")).thenThrow(new RankingException(Reason.INVALID_GAME));
        when(service.top("lore-challenge")).thenThrow(new RankingException(Reason.UNAVAILABLE));

        mockMvc.perform(get("/api/ranking").param("game", "who-am-i"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.game").value("who-am-i"))
                .andExpect(jsonPath("$.entries", hasSize(0)));
        mockMvc.perform(get("/api/ranking").param("game", "nonsense")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/ranking").param("game", "lore-challenge"))
                .andExpect(status().isServiceUnavailable());
        verify(service).top(eq("who-am-i"));
    }
}
