package com.wunderlore.ranking;

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

import com.wunderlore.game.whosaidit.RoundCreationRateLimiter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Answers 429 to leaderboard publishing beyond a global limit, before the controller sees the
// request. It reuses the sliding-window limiter of the games and follows their filters: only
// POST /api/ranking/entries is counted, all callers share one quota, no client address or proxy
// header is read, and the state is in this process (several instances would each have their own
// quota). Reading the leaderboard is never limited.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
class RankingPublishRateLimitFilter extends OncePerRequestFilter {

    static final String PUBLISH_PATH = "/api/ranking/entries";

    static final String DETAIL = "Too many publish requests. Please try again later.";

    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

    // Fixed text only, so there is nothing to escape.
    private static final String BODY = "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\","
            + "\"status\":429,\"detail\":\"" + DETAIL + "\",\"instance\":\"" + PUBLISH_PATH + "\"}";

    private final RoundCreationRateLimiter limiter;
    private final boolean enabled;

    RankingPublishRateLimitFilter(
            Clock clock,
            @Value("${wunderlore.rate-limit.ranking-publish.enabled:true}") boolean enabled,
            @Value("${wunderlore.rate-limit.ranking-publish.limit:60}") int limit,
            @Value("${wunderlore.rate-limit.ranking-publish.window:60s}") Duration window) {
        this.enabled = enabled;
        this.limiter = new RoundCreationRateLimiter(clock, limit, window);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled
                || !HttpMethod.POST.matches(request.getMethod())
                || !PUBLISH_PATH.equals(PATH_HELPER.getPathWithinApplication(request));
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
