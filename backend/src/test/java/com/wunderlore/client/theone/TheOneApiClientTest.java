package com.wunderlore.client.theone;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

class TheOneApiClientTest {

    @Test
    void shouldReturnBooksFromExternalApi() {
        RestClient.Builder builder = RestClient.builder();

        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://the-one-api.dev/v2/book"))
                .andExpect(method(GET))
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

        TheOneApiClient client = new TheOneApiClient(builder);

        var response = client.getBooks();

        assertEquals(1, response.docs().size());
        assertEquals("book-123", response.docs().get(0)._id());
        assertEquals("Example Book", response.docs().get(0).name());

        server.verify();
    }
}
