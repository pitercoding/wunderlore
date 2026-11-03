package com.wunderlore.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;

// Rejects request bodies above a fixed size with 413, before any controller or JSON parser sees
// them. Bean Validation and Jackson's own limits only apply after (or while) a body is parsed,
// and Tomcat's limits cover form posts, not JSON. A Jackson-level limit would also be shared
// with the responses The One API client parses, which are far larger than any request.
//
// The default of 4 KB is about nine times the largest request the API accepts: the Which
// Character result with ten answers is under 450 bytes, and a Who Said It answer is under 100.
// An endpoint that needs bigger bodies must raise wunderlore.http.max-request-body-size.
//
// - A declared Content-Length above the limit is refused without reading anything. A declared
//   length within the limit is a real bound: the server never delivers more than it declares.
// - A body with no declared length (chunked) cannot be trusted, so at most limit + 1 bytes are
//   read ahead. If the extra byte is there the request is refused; otherwise those bytes are
//   handed on for the controller to read as usual. Memory is bounded by the limit.
// The check applies to every endpoint, so the limit covers any future route too.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestBodySizeLimitFilter extends OncePerRequestFilter {

    // Methods that may carry a body. Others have none, so an unknown length is nothing to read.
    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final int maxBytes;

    RequestBodySizeLimitFilter(
            @Value("${wunderlore.http.max-request-body-size:4KB}") DataSize maxSize) {
        long bytes = maxSize.toBytes();

        if (bytes < 1 || bytes >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Maximum request body size must be between 1 byte and 2 GB");
        }

        this.maxBytes = (int) bytes;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long declaredLength = request.getContentLengthLong();

        if (declaredLength > maxBytes) {
            reject(response);

            return;
        }

        if (declaredLength >= 0 || !BODY_METHODS.contains(request.getMethod())) {
            chain.doFilter(request, response);

            return;
        }

        byte[] body = request.getInputStream().readNBytes(maxBytes + 1);

        if (body.length > maxBytes) {
            reject(response);

            return;
        }

        chain.doFilter(new BufferedBodyRequest(request, body), response);
    }

    // Closing the connection stops the server from reading on through a body it refused.
    private static void reject(HttpServletResponse response) throws IOException {
        response.setHeader(HttpHeaders.CONNECTION, "close");
        response.sendError(HttpStatus.CONTENT_TOO_LARGE.value());
    }

    // The bytes read ahead, served again to whoever reads the body.
    private static final class BufferedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private BufferedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new BufferedInputStream(body);
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), charset()));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        private Charset charset() {
            String name = getCharacterEncoding();

            try {
                return name == null ? StandardCharsets.UTF_8 : Charset.forName(name);
            } catch (IllegalArgumentException exception) {
                return StandardCharsets.UTF_8;
            }
        }
    }

    private static final class BufferedInputStream extends ServletInputStream {

        private final ByteArrayInputStream in;

        private BufferedInputStream(byte[] body) {
            this.in = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return in.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            return in.read(buffer, offset, length);
        }

        @Override
        public boolean isFinished() {
            return in.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            throw new UnsupportedOperationException("Asynchronous reads are not supported");
        }
    }
}
