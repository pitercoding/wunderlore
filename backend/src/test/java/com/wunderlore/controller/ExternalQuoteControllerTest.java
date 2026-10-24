package com.wunderlore.controller;

import com.wunderlore.client.theone.TheOneApiClient;
import com.wunderlore.client.theone.dto.ExternalQuote;
import com.wunderlore.client.theone.dto.ExternalQuoteResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExternalQuoteController.class)
class ExternalQuoteControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private TheOneApiClient theOneApiClient;

        @Test
        void shouldReturnQuotesUsingDefaultPagination() throws Exception {
                ExternalQuote quote = new ExternalQuote(
                                "quote-123",
                                "Example dialogue",
                                "movie-123",
                                "character-123",
                                "quote-123");

                ExternalQuoteResponse response = new ExternalQuoteResponse(
                                List.of(quote),
                                1,
                                20,
                                null,
                                1,
                                1);

                when(theOneApiClient.getQuotes(1, 20)).thenReturn(response);

                mockMvc.perform(get("/api/catalog/quotes"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.docs[0]._id").value("quote-123"))
                                .andExpect(jsonPath("$.docs[0].dialog").value("Example dialogue"))
                                .andExpect(jsonPath("$.docs[0].movie").value("movie-123"))
                                .andExpect(jsonPath("$.docs[0].character").value("character-123"))
                                .andExpect(jsonPath("$.docs[0].id").value("quote-123"))
                                .andExpect(jsonPath("$.total").value(1))
                                .andExpect(jsonPath("$.limit").value(20))
                                .andExpect(jsonPath("$.page").value(1))
                                .andExpect(jsonPath("$.pages").value(1))
                                .andExpect(jsonPath("$.offset").doesNotExist());

                verify(theOneApiClient).getQuotes(1, 20);
        }

        @Test
        void shouldForwardCustomPaginationParameters() throws Exception {
                ExternalQuoteResponse response = new ExternalQuoteResponse(
                                List.of(),
                                11,
                                5,
                                null,
                                2,
                                3);

                when(theOneApiClient.getQuotes(2, 5)).thenReturn(response);

                mockMvc.perform(get("/api/catalog/quotes")
                                .param("page", "2")
                                .param("limit", "5"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.total").value(11))
                                .andExpect(jsonPath("$.limit").value(5))
                                .andExpect(jsonPath("$.page").value(2))
                                .andExpect(jsonPath("$.pages").value(3));

                verify(theOneApiClient).getQuotes(2, 5);
        }

        @Test
        void shouldRejectPageZero() throws Exception {
                mockMvc.perform(get("/api/catalog/quotes")
                                .param("page", "0"))
                                .andExpect(status().isBadRequest());

                verifyNoInteractions(theOneApiClient);
        }

        @Test
        void shouldRejectLimitZero() throws Exception {
                mockMvc.perform(get("/api/catalog/quotes")
                                .param("limit", "0"))
                                .andExpect(status().isBadRequest());

                verifyNoInteractions(theOneApiClient);
        }

        @Test
        void shouldRejectLimitAboveMaximum() throws Exception {
                mockMvc.perform(get("/api/catalog/quotes")
                                .param("limit", "101"))
                                .andExpect(status().isBadRequest());

                verifyNoInteractions(theOneApiClient);
        }
}
