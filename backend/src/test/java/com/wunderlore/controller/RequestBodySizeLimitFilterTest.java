package com.wunderlore.controller;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Plain unit tests: the filter has no Spring dependencies beyond its constructor argument.
class RequestBodySizeLimitFilterTest {

    private static final int LIMIT = 100;

    private final RequestBodySizeLimitFilter filter =
            new RequestBodySizeLimitFilter(DataSize.ofBytes(LIMIT));

    // A request whose length is not declared, as with chunked transfer encoding. The body
    // comes from a stream that counts how much of it was read.
    private static final class ChunkedRequest extends MockHttpServletRequest {

        private final InputStream body;

        private ChunkedRequest(String method, InputStream body) {
            super(method, "/api/test");
            this.body = body;
            setContentType("application/json");
            addHeader("Transfer-Encoding", "chunked");
        }

        @Override
        public long getContentLengthLong() {
            return -1;
        }

        @Override
        public int getContentLength() {
            return -1;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new DelegatingServletInputStream(body);
        }
    }

    private static byte[] bytes(int length) {
        return "x".repeat(length).getBytes(StandardCharsets.UTF_8);
    }

    private static MockHttpServletRequest declared(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");

        request.setContentType("application/json");
        request.setContent(body);

        return request;
    }

    private MockFilterChain run(MockHttpServletRequest request, MockHttpServletResponse response)
            throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        return chain;
    }

    @Test
    void shouldPassABodyWithADeclaredLengthWithinTheLimitUntouched() throws Exception {
        MockHttpServletRequest request = declared(bytes(LIMIT));
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain chain = run(request, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void shouldRefuseADeclaredLengthAboveTheLimitWithoutReadingTheBody() throws Exception {
        AtomicLong consumed = new AtomicLong();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test") {
            @Override
            public long getContentLengthLong() {
                return LIMIT + 1;
            }

            @Override
            public ServletInputStream getInputStream() {
                consumed.incrementAndGet();

                return super.getInputStream();
            }
        };
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain chain = run(request, response);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getHeader("Connection")).isEqualTo("close");
        assertThat(chain.getRequest()).isNull();
        assertThat(consumed).hasValue(0);
    }

    @Test
    void shouldPassAChunkedBodyAtExactlyTheLimitAndServeItAgain() throws Exception {
        byte[] body = bytes(LIMIT);
        ChunkedRequest request = new ChunkedRequest("POST", new ByteArrayInputStream(body));
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain chain = run(request, response);

        HttpServletRequest passed = (HttpServletRequest) chain.getRequest();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(passed.getInputStream().readAllBytes()).isEqualTo(body);
        assertThat(passed.getContentLengthLong()).isEqualTo(LIMIT);
    }

    @Test
    void shouldServeTheBufferedBodyThroughTheReaderToo() throws Exception {
        ChunkedRequest request = new ChunkedRequest(
                "POST", new ByteArrayInputStream("{\"name\":\"Gandalf\"}".getBytes(StandardCharsets.UTF_8)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain chain = run(request, response);

        HttpServletRequest passed = (HttpServletRequest) chain.getRequest();

        assertThat(passed.getReader().readLine()).isEqualTo("{\"name\":\"Gandalf\"}");
    }

    @Test
    void shouldRefuseAChunkedBodyOneByteOverTheLimit() throws Exception {
        ChunkedRequest request = new ChunkedRequest("POST", new ByteArrayInputStream(bytes(LIMIT + 1)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain chain = run(request, response);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void shouldNeverReadMoreThanTheLimitPlusOneByteOfAnUnboundedBody() throws Exception {
        AtomicLong served = new AtomicLong();
        InputStream endless = new InputStream() {
            @Override
            public int read() {
                served.incrementAndGet();

                return 'x';
            }

            @Override
            public int read(byte[] buffer, int offset, int length) {
                served.addAndGet(length);

                java.util.Arrays.fill(buffer, offset, offset + length, (byte) 'x');

                return length;
            }
        };
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain chain = run(new ChunkedRequest("POST", endless), response);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).isNull();
        assertThat(served.get()).isLessThanOrEqualTo(LIMIT + 1);
    }

    @Test
    void shouldLeaveRequestsWithoutABodyAlone() throws Exception {
        for (String method : new String[] { "GET", "HEAD", "OPTIONS" }) {
            ChunkedRequest request = new ChunkedRequest(method, new ByteArrayInputStream(bytes(LIMIT * 10)));
            MockHttpServletResponse response = new MockHttpServletResponse();

            MockFilterChain chain = run(request, response);

            assertThat(response.getStatus()).as(method).isEqualTo(200);
            assertThat(chain.getRequest()).as(method).isSameAs(request);
        }
    }

    @Test
    void shouldPassABodylessPostWithNoDeclaredLength() throws Exception {
        ChunkedRequest request = new ChunkedRequest("POST", InputStream.nullInputStream());
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain chain = run(request, response);

        HttpServletRequest passed = (HttpServletRequest) chain.getRequest();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(passed.getInputStream().readAllBytes()).isEmpty();
    }

    @Test
    void shouldRejectALimitThatIsNotPositive() {
        assertThatThrownBy(() -> new RequestBodySizeLimitFilter(DataSize.ofBytes(0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RequestBodySizeLimitFilter(DataSize.ofBytes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
