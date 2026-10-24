package com.wunderlore.client.theone;

import com.wunderlore.client.theone.dto.ExternalQuoteResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

class TheOneApiClientTest {

    private static final String BASE_URL = "https://the-one-api.dev/v2";

    @Test
    void shouldReturnBooksWithoutAuthorizationHeader() {
        RestClient.Builder builder = RestClient.builder();

        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(BASE_URL + "/book"))
                .andExpect(method(GET))
                .andExpect(headerDoesNotExist(AUTHORIZATION))
                .andRespond(withSuccess("""
                        {
                            "docs": [
                                {
                                    "_id": "book-123",
                                    "name": "Example Book"
                                }
                            ],
                            "total": 1,
                            "limit": 1000,
                            "offset": 0,
                            "page": 1,
                            "pages": 1
                        }
                        """, MediaType.APPLICATION_JSON));

        TheOneApiClient client = new TheOneApiClient(builder, BASE_URL, "test-token");

        var response = client.getBooks();

        assertEquals(1, response.docs().size());
        assertEquals("book-123", response.docs().get(0)._id());
        assertEquals("Example Book", response.docs().get(0).name());

        server.verify();
    }

    @Test
    void shouldSendBearerTokenForAuthenticatedRequest() {
        RestClient.Builder builder = RestClient.builder();

        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(BASE_URL + "/quote"))
                .andExpect(method(GET))
                .andExpect(header(AUTHORIZATION, "Bearer test-token"))
                .andRespond(withSuccess("""
                        {
                            "docs": [],
                            "total": 0,
                            "limit": 1000,
                            "offset": 0,
                            "page": 1,
                            "pages": 1
                        }
                        """, MediaType.APPLICATION_JSON));

        TheOneApiClient client = new TheOneApiClient(builder, BASE_URL, "test-token");

        ExternalQuoteResponse response = client.authenticatedGet("/quote")
                .retrieve()
                .body(ExternalQuoteResponse.class);

        assertEquals(0, response.docs().size());
        assertEquals(0, response.total());

        server.verify();
    }

    @Test
    void shouldRejectAuthenticatedRequestWhenTokenIsMissing() {
        RestClient.Builder builder = RestClient.builder();

        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        TheOneApiClient client = new TheOneApiClient(builder, BASE_URL, "");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> client.authenticatedGet("/quote"));

        assertEquals(
                "THE_ONE_API_ACCESS_TOKEN is not configured",
                exception.getMessage());

        server.verify();
    }

    @Test
    void shouldReturnQuotesWithPaginationAndAuthorization() {
        RestClient.Builder builder = RestClient.builder();

        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(BASE_URL + "/quote?page=2&limit=5"))
                .andExpect(method(GET))
                .andExpect(header(AUTHORIZATION, "Bearer test-token"))
                .andRespond(withSuccess("""
                        {
                            "docs": [
                                {
                                    "_id": "quote-123",
                                    "dialog": "Example dialogue",
                                    "movie": "movie-123",
                                    "character": "character-123",
                                    "id": "quote-123"
                                }
                            ],
                            "total": 11,
                            "limit": 5,
                            "page": 2,
                            "pages": 3
                        }
                        """, MediaType.APPLICATION_JSON));

        TheOneApiClient client = new TheOneApiClient(builder, BASE_URL, "test-token");

        var response = client.getQuotes(2, 5);

        assertEquals(1, response.docs().size());
        assertEquals("quote-123", response.docs().get(0)._id());
        assertEquals("Example dialogue", response.docs().get(0).dialog());
        assertEquals("movie-123", response.docs().get(0).movie());
        assertEquals("character-123", response.docs().get(0).character());
        assertEquals("quote-123", response.docs().get(0).id());
        assertEquals(11, response.total());
        assertEquals(5, response.limit());
        assertNull(response.offset());
        assertEquals(2, response.page());
        assertEquals(3, response.pages());

        server.verify();
    }

    @Test
    void shouldNotRequestQuotesWhenTokenIsMissing() {
        RestClient.Builder builder = RestClient.builder();

        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        TheOneApiClient client = new TheOneApiClient(builder, BASE_URL, "");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> client.getQuotes(1, 20));

        assertEquals(
                "THE_ONE_API_ACCESS_TOKEN is not configured",
                exception.getMessage());

        server.verify();
    }

    @Test
    void shouldPropagateUnauthorizedResponseWhenFetchingQuotes() {
        RestClient.Builder builder = RestClient.builder();

        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(BASE_URL + "/quote?page=1&limit=20"))
                .andExpect(method(GET))
                .andExpect(header(AUTHORIZATION, "Bearer invalid-token"))
                .andRespond(withUnauthorizedRequest());

        TheOneApiClient client = new TheOneApiClient(builder, BASE_URL, "invalid-token");

        assertThrows(
                HttpClientErrorException.Unauthorized.class,
                () -> client.getQuotes(1, 20));

        server.verify();
    }
}
