package com.wunderlore.controller;

import com.wunderlore.client.theone.TheOneApiClient;
import com.wunderlore.client.theone.dto.ExternalCharacter;
import com.wunderlore.client.theone.dto.ExternalCharacterResponse;
import com.wunderlore.client.theone.dto.ExternalQuote;
import com.wunderlore.client.theone.dto.ExternalQuoteResponse;
import com.wunderlore.lore.LoreDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.util.List;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Wires the real LoreDataService behind the catalog so only the HTTP client is mocked.
@WebMvcTest(ExternalQuoteController.class)
@Import({ LoreDataService.class, ExternalQuoteControllerCacheTest.ClockConfiguration.class })
class ExternalQuoteControllerCacheTest {

        private static final int PAGE_SIZE = 100;

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private TheOneApiClient theOneApiClient;

        @Test
        void shouldLoadUpstreamDataOnceAcrossCatalogRequests() throws Exception {
                ExternalQuote q1 = new ExternalQuote("q1", "Po-tay-toes!", "movie-1", "sam", "q1");
                ExternalQuote q2 = new ExternalQuote("q2", "Fly, you fools!", "movie-1", "gandalf", "q2");
                ExternalQuote q3 = new ExternalQuote("q3", "", "movie-1", "unknown", "q3");

                when(theOneApiClient.getQuotes(1, PAGE_SIZE))
                                .thenReturn(new ExternalQuoteResponse(List.of(q1, q2), 3, PAGE_SIZE, null, 1, 2));
                when(theOneApiClient.getQuotes(2, PAGE_SIZE))
                                .thenReturn(new ExternalQuoteResponse(List.of(q3), 3, PAGE_SIZE, null, 2, 2));
                when(theOneApiClient.getCharacters(1, PAGE_SIZE))
                                .thenReturn(new ExternalCharacterResponse(
                                                List.of(new ExternalCharacter("sam", "Samwise Gamgee")),
                                                1, PAGE_SIZE, null, 1, 1));

                mockMvc.perform(get("/api/catalog/quotes").param("limit", "2"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.docs[0]._id").value("q1"))
                                .andExpect(jsonPath("$.docs[1]._id").value("q2"))
                                .andExpect(jsonPath("$.docs[0].characterName").value("Samwise Gamgee"))
                                .andExpect(jsonPath("$.total").value(3))
                                .andExpect(jsonPath("$.pages").value(2));

                mockMvc.perform(get("/api/catalog/quotes").param("page", "2").param("limit", "2"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.docs.length()").value(1))
                                .andExpect(jsonPath("$.docs[0]._id").value("q3"));

                mockMvc.perform(get("/api/catalog/quotes"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.docs.length()").value(3));

                verify(theOneApiClient, times(1)).getQuotes(1, PAGE_SIZE);
                verify(theOneApiClient, times(1)).getQuotes(2, PAGE_SIZE);
                verify(theOneApiClient, times(1)).getCharacters(1, PAGE_SIZE);
                verifyNoMoreInteractions(theOneApiClient);
        }

        @TestConfiguration
        static class ClockConfiguration {

                @Bean
                Clock clock() {
                        return Clock.systemUTC();
                }
        }
}
