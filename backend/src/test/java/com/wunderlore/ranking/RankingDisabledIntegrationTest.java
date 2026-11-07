package com.wunderlore.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// The default configuration: persistence is off, no database exists, and the application must
// still start and serve every game. The leaderboard answers 503 instead of failing the backend.
@SpringBootTest
@AutoConfigureMockMvc
class RankingDisabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Test
    void shouldStartWithoutADatabaseOrARankingRepository() {
        assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
        assertThat(context.getBeanNamesForType(RankingEntryRepository.class)).isEmpty();
    }

    @Test
    void shouldAnswer503ToPublishingAndReadingTheLeaderboard() throws Exception {
        mockMvc.perform(post("/api/ranking/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"who-am-i\",\"gameId\":\"g\",\"nickname\":\"Frodo\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("The leaderboard is unavailable right now"));
        mockMvc.perform(get("/api/ranking")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void shouldStillValidateRequestsAndKeepTheGamesWorking() throws Exception {
        mockMvc.perform(post("/api/ranking/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"which-character\",\"gameId\":\"g\",\"nickname\":\"Frodo\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/games/who-am-i/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"difficulty\":\"EASY\"}"))
                .andExpect(status().isOk());
    }
}
