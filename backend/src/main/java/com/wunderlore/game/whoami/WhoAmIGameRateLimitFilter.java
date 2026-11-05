package com.wunderlore.game.whoami;

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

// Answers 429 to game creation beyond a global limit, before the controller or the registry see
// the request: without it a script could fill every slot of the registry and keep real players
// out. It reuses Who Said It's sliding-window limiter and follows the Lore Challenge filter: only
// POST /api/games/who-am-i/games is counted, all callers share one quota, no client address or
// proxy header is read, and the state is in this process (several instances would each have
// their own quota). Answering, reading rounds and results are never limited.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 12)
class WhoAmIGameRateLimitFilter extends OncePerRequestFilter {

    static final String GAMES_PATH = "/api/games/who-am-i/games";

    static final String DETAIL = "Too many game requests. Please try again later.";

    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

    // Fixed text only, so there is nothing to escape.
    private static final String BODY = "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\","
            + "\"status\":429,\"detail\":\"" + DETAIL + "\",\"instance\":\"" + GAMES_PATH + "\"}";

    private final RoundCreationRateLimiter limiter;
    private final boolean enabled;

    WhoAmIGameRateLimitFilter(
            Clock clock,
            @Value("${wunderlore.rate-limit.who-am-i-game-creation.enabled:true}") boolean enabled,
            @Value("${wunderlore.rate-limit.who-am-i-game-creation.limit:60}") int limit,
            @Value("${wunderlore.rate-limit.who-am-i-game-creation.window:60s}") Duration window) {
        this.enabled = enabled;
        this.limiter = new RoundCreationRateLimiter(clock, limit, window);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled
                || !HttpMethod.POST.matches(request.getMethod())
                || !GAMES_PATH.equals(PATH_HELPER.getPathWithinApplication(request));
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
