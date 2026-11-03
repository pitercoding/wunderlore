package com.wunderlore.game.whosaidit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Answers 429 to round creation beyond a global limit, before the controller or the registry see
// the request. Only POST /api/games/who-said-it/rounds is counted: /answer and every other route
// pass untouched, and so does a path that would not reach the controller (it is a 404 anyway, and
// counting it would let such requests use up the quota).
//
// The limit is for all callers together. No client address or proxy header is read, so there is
// nothing for a caller to spoof. The state is in this process: with several instances each has
// its own quota.
//
// Runs right after RequestBodySizeLimitFilter, which refuses oversized bodies first and so does
// not use up quota.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class RoundCreationRateLimitFilter extends OncePerRequestFilter {

    static final String ROUNDS_PATH = "/api/games/who-said-it/rounds";

    static final String DETAIL = "Too many round requests. Please try again later.";

    // Strips path parameters (;x=y), decodes and normalizes, the way the MVC mapping sees the path.
    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

    // Fixed text only, so there is nothing to escape. Same fields ProblemDetail renders.
    private static final String BODY = "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\","
            + "\"status\":429,\"detail\":\"" + DETAIL + "\",\"instance\":\"" + ROUNDS_PATH + "\"}";

    private final RoundCreationRateLimiter limiter;
    private final boolean enabled;

    RoundCreationRateLimitFilter(
            Clock clock,
            @Value("${wunderlore.rate-limit.round-creation.enabled:true}") boolean enabled,
            @Value("${wunderlore.rate-limit.round-creation.limit:120}") int limit,
            @Value("${wunderlore.rate-limit.round-creation.window:60s}") Duration window) {
        this.enabled = enabled;
        this.limiter = new RoundCreationRateLimiter(clock, limit, window);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled
                || !HttpMethod.POST.matches(request.getMethod())
                || !ROUNDS_PATH.equals(PATH_HELPER.getPathWithinApplication(request));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RoundCreationRateLimiter.Decision decision = limiter.tryAcquire();

        if (decision.allowed()) {
            chain.doFilter(request, response);

            return;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(BODY);
    }
}
