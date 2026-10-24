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
    void shouldReturnQuotesFromCatalogEndpoint() throws Exception {
        ExternalQuote quote = new ExternalQuote(
                "quote-123",
                "Example dialogue",
                "movie-123",
                "character-123",
                "quote-123");

        ExternalQuoteResponse response = new ExternalQuoteResponse(
                List.of(quote),
                1,
                1000,
                0,
                1,
                1);

        when(theOneApiClient.getQuotes()).thenReturn(response);

        mockMvc.perform(get("/api/catalog/quotes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.docs[0]._id").value("quote-123"))
                .andExpect(jsonPath("$.docs[0].dialog").value("Example dialogue"))
                .andExpect(jsonPath("$.docs[0].movie").value("movie-123"))
                .andExpect(jsonPath("$.docs[0].character").value("character-123"))
                .andExpect(jsonPath("$.docs[0].id").value("quote-123"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.limit").value(1000))
                .andExpect(jsonPath("$.offset").value(0))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pages").value(1));

        verify(theOneApiClient).getQuotes();
    }
}
