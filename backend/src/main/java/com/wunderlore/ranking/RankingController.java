package com.wunderlore.ranking;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wunderlore.ranking.dto.LeaderboardResponse;
import com.wunderlore.ranking.dto.PublishRequest;
import com.wunderlore.ranking.dto.PublishedEntryResponse;

import jakarta.validation.Valid;

// The global leaderboard: publishing the result of a finished game and reading the best results.
@RestController
@RequestMapping("/api/ranking")
public class RankingController {

    static final String INVALID_REQUEST_DETAIL = "Invalid request";

    private final RankingService service;

    public RankingController(RankingService service) {
        this.service = service;
    }

    // 201 when the entry was just created, 200 when the game had already been published.
    @PostMapping("/entries")
    public ResponseEntity<PublishedEntryResponse> publish(@Valid @RequestBody PublishRequest request) {
        RankingService.Published published = service.publish(
                request.gameType(), request.gameId(), request.nickname());

        return ResponseEntity
                .status(published.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(PublishedEntryResponse.from(published.entry(), !published.created()));
    }

    // game: optional slug of one game (lore-challenge, who-am-i, where-in-middle-earth, who-said-it).
    @GetMapping
    public LeaderboardResponse leaderboard(@RequestParam(required = false) String game) {
        return LeaderboardResponse.from(game, service.top(game));
    }

    @ExceptionHandler(RankingException.class)
    public ProblemDetail handleRanking(RankingException exception) {
        return ProblemDetail.forStatusAndDetail(
                exception.reason().status(),
                exception.reason().detail());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class
    })
    public ProblemDetail handleInvalidRequest(Exception exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, INVALID_REQUEST_DETAIL);
    }
}
