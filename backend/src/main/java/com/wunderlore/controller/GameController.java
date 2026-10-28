package com.wunderlore.controller;

import com.wunderlore.client.theone.TheOneApiNotConfiguredException;
import com.wunderlore.game.GameRound;
import com.wunderlore.game.GameRoundRegistry;
import com.wunderlore.game.GameRoundResponse;
import com.wunderlore.game.WhoSaidItService;
import com.wunderlore.game.AnswerRequest;
import com.wunderlore.game.AnswerResponse;
import com.wunderlore.game.InvalidCharacterOptionException;
import com.wunderlore.game.RoundAlreadyAnsweredException;
import com.wunderlore.game.RoundCapacityExceededException;
import com.wunderlore.game.RoundNotFoundException;
import com.wunderlore.lore.LoreDataUnavailableException;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

@RestController
@RequestMapping("/api/game")
public class GameController {

    private final WhoSaidItService whoSaidItService;
    private final GameRoundRegistry gameRoundRegistry;

    public GameController(
            WhoSaidItService whoSaidItService,
            GameRoundRegistry gameRoundRegistry) {
        this.whoSaidItService = whoSaidItService;
        this.gameRoundRegistry = gameRoundRegistry;
    }

    @GetMapping("/round")
    public GameRoundResponse getRound() {
        GameRound round = whoSaidItService.generateRound();
        String roundId = gameRoundRegistry.register(round);

        return GameRoundResponse.from(roundId, round);
    }

    @PostMapping("/answer")
    public AnswerResponse answer(@Valid @RequestBody AnswerRequest request) {
        return gameRoundRegistry.answer(request);
    }

    @ExceptionHandler({
            RestClientException.class,
            LoreDataUnavailableException.class,
            TheOneApiNotConfiguredException.class,
            IllegalStateException.class
    })
    public ProblemDetail handleRoundGenerationFailure(Exception exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Unable to generate a game round right now");
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class
    })
    public ProblemDetail handleInvalidAnswerRequest(Exception exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Invalid answer request");
    }

    @ExceptionHandler(InvalidCharacterOptionException.class)
    public ProblemDetail handleInvalidCharacterOption(
            InvalidCharacterOptionException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.getMessage());
    }

    @ExceptionHandler(RoundNotFoundException.class)
    public ProblemDetail handleRoundNotFound(
            RoundNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage());
    }

    @ExceptionHandler(RoundAlreadyAnsweredException.class)
    public ProblemDetail handleRoundAlreadyAnswered(
            RoundAlreadyAnsweredException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage());
    }

    @ExceptionHandler(RoundCapacityExceededException.class)
    public ProblemDetail handleRoundCapacityExceeded(
            RoundCapacityExceededException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Too many active rounds, try again later");
    }
}
