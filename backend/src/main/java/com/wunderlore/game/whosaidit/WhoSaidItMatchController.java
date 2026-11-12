package com.wunderlore.game.whosaidit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import com.wunderlore.game.whosaidit.dto.MatchAnswerRequest;
import com.wunderlore.game.whosaidit.dto.MatchAnswerResponse;
import com.wunderlore.game.whosaidit.dto.MatchQuestionResponse;
import com.wunderlore.game.whosaidit.dto.MatchResultResponse;
import com.wunderlore.lore.LoreBacked;

import jakarta.validation.Valid;

// The competitive Who Said It? mode: a match of ten quotes whose sequence, answers and score the
// backend owns. The single-round game lives in WhoSaidItController. The client gets the
// current question, answers it, and learns the correct characters only from the finished result.
@RestController
@RequestMapping("/api/games/who-said-it/matches")
@LoreBacked(unavailableDetail = WhoSaidItMatchController.MATCH_UNAVAILABLE_DETAIL, failure = "Match creation failed")
public class WhoSaidItMatchController {

    private static final Logger log = LoggerFactory.getLogger(WhoSaidItMatchController.class);

    public static final String MATCH_UNAVAILABLE_DETAIL = "Unable to start a match right now";

    static final String INVALID_REQUEST_DETAIL = "Invalid request";

    private final WhoSaidItMatchService service;
    private final WhoSaidItMatchRegistry registry;

    public WhoSaidItMatchController(WhoSaidItMatchService service, WhoSaidItMatchRegistry registry) {
        this.service = service;
        this.registry = registry;
    }

    @PostMapping
    public MatchQuestionResponse createMatch() {
        String matchId = registry.register(service.createMatch());

        return MatchQuestionResponse.from(matchId, registry.currentQuestion(matchId));
    }

    @GetMapping("/{matchId}/question")
    public MatchQuestionResponse currentQuestion(@PathVariable String matchId) {
        return MatchQuestionResponse.from(matchId, registry.currentQuestion(matchId));
    }

    @PostMapping("/{matchId}/answer")
    public MatchAnswerResponse answer(
            @PathVariable String matchId,
            @Valid @RequestBody MatchAnswerRequest request) {
        return MatchAnswerResponse.from(
                matchId,
                registry.answer(matchId, request.questionNumber(), request.characterId()));
    }

    @GetMapping("/{matchId}/result")
    public MatchResultResponse result(@PathVariable String matchId) {
        return MatchResultResponse.from(matchId, registry.result(matchId));
    }

    @ExceptionHandler(WhoSaidItMatchException.class)
    public ProblemDetail handleMatch(WhoSaidItMatchException exception) {
        return ProblemDetail.forStatusAndDetail(
                exception.reason().status(),
                exception.reason().detail());
    }

    // Raised with fixed messages when the loaded lore cannot form ten distinct questions.
    @ExceptionHandler(WhoSaidItInsufficientDataException.class)
    public ProblemDetail handleInsufficientData(WhoSaidItInsufficientDataException exception) {
        log.warn("Match creation failed: {}: {}", exception.getClass().getName(), exception.getMessage());

        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, MATCH_UNAVAILABLE_DETAIL);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class
    })
    public ProblemDetail handleInvalidRequest(Exception exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, INVALID_REQUEST_DETAIL);
    }
}
