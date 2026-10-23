package com.wunderlore.client.theone;

import com.wunderlore.client.theone.dto.ExternalBookResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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

        ExternalBookResponse response = client.authenticatedGet("/quote")
                .retrieve()
                .body(ExternalBookResponse.class);

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
}
