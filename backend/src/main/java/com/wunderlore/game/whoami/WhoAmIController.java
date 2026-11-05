package com.wunderlore.game.whoami;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wunderlore.game.whoami.dto.AnswerRequest;
import com.wunderlore.game.whoami.dto.AnswerResponse;
import com.wunderlore.game.whoami.dto.CreateGameRequest;
import com.wunderlore.game.whoami.dto.ResultResponse;
import com.wunderlore.game.whoami.dto.RoundResponse;

import jakarta.validation.Valid;

// Who Am I? works from curated characters in the project, not from The One API, so it has no
// @LoreBacked and never depends on the lore data being loaded. The backend owns the sequence and
// the answers: the client asks for the current round, answers its current clue, and learns who
// the character was only once the round is over.
@RestController
@RequestMapping("/api/games/who-am-i/games")
public class WhoAmIController {

    static final String INVALID_REQUEST_DETAIL = "Invalid request";

    private final WhoAmIService service;
    private final WhoAmIGameRegistry registry;

    public WhoAmIController(WhoAmIService service, WhoAmIGameRegistry registry) {
        this.service = service;
        this.registry = registry;
    }

    @PostMapping
    public RoundResponse createGame(@Valid @RequestBody CreateGameRequest request) {
        String gameId = registry.register(service.createGame(request.difficulty()));

        return RoundResponse.from(gameId, registry.currentRound(gameId));
    }

    @GetMapping("/{gameId}/round")
    public RoundResponse currentRound(@PathVariable String gameId) {
        return RoundResponse.from(gameId, registry.currentRound(gameId));
    }

    @PostMapping("/{gameId}/answer")
    public AnswerResponse answer(
            @PathVariable String gameId,
            @Valid @RequestBody AnswerRequest request) {
        return AnswerResponse.from(
                gameId,
                registry.answer(
                        gameId, request.roundNumber(), request.clueNumber(), request.optionId()));
    }

    @GetMapping("/{gameId}/result")
    public ResultResponse result(@PathVariable String gameId) {
        return ResultResponse.from(gameId, registry.result(gameId));
    }

    @ExceptionHandler(WhoAmIException.class)
    public ProblemDetail handleWhoAmI(WhoAmIException exception) {
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
