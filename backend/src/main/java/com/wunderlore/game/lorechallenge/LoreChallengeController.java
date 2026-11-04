package com.wunderlore.game.lorechallenge;

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

import com.wunderlore.game.lorechallenge.dto.AnswerRequest;
import com.wunderlore.game.lorechallenge.dto.AnswerResponse;
import com.wunderlore.game.lorechallenge.dto.CreateMatchRequest;
import com.wunderlore.game.lorechallenge.dto.MatchQuestionResponse;
import com.wunderlore.game.lorechallenge.dto.ResultResponse;

import jakarta.validation.Valid;

// Lore Challenge works from curated questions in the project, not from The One API, so it has
// no @LoreBacked and never depends on the lore data being loaded. The backend owns the
// sequence: the client asks for the current question, answers it, and only then is the next
// one served.
@RestController
@RequestMapping("/api/games/lore-challenge/matches")
public class LoreChallengeController {

    static final String INVALID_REQUEST_DETAIL = "Invalid request";

    private final LoreChallengeService service;
    private final LoreChallengeMatchRegistry registry;

    public LoreChallengeController(LoreChallengeService service, LoreChallengeMatchRegistry registry) {
        this.service = service;
        this.registry = registry;
    }

    @PostMapping
    public MatchQuestionResponse createMatch(@Valid @RequestBody CreateMatchRequest request) {
        LoreChallengeMatch match = service.createMatch(request.selectedCategory(), request.difficulty());
        String matchId = registry.register(match);

        return MatchQuestionResponse.from(matchId, registry.currentQuestion(matchId));
    }

    @GetMapping("/{matchId}/question")
    public MatchQuestionResponse currentQuestion(@PathVariable String matchId) {
        return MatchQuestionResponse.from(matchId, registry.currentQuestion(matchId));
    }

    @PostMapping("/{matchId}/answer")
    public AnswerResponse answer(
            @PathVariable String matchId,
            @Valid @RequestBody AnswerRequest request) {
        return AnswerResponse.from(
                matchId,
                registry.answer(matchId, request.questionId(), request.optionId()));
    }

    @GetMapping("/{matchId}/result")
    public ResultResponse result(@PathVariable String matchId) {
        return ResultResponse.from(matchId, registry.result(matchId));
    }

    @ExceptionHandler(LoreChallengeException.class)
    public ProblemDetail handleLoreChallenge(LoreChallengeException exception) {
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
