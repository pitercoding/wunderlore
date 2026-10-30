package com.wunderlore.controller;

import com.wunderlore.game.GameRound;
import com.wunderlore.game.GameRoundRegistry;
import com.wunderlore.game.GameRoundResponse;
import com.wunderlore.game.InsufficientGameDataException;
import com.wunderlore.game.WhoSaidItService;
import com.wunderlore.game.AnswerRequest;
import com.wunderlore.game.AnswerResponse;
import com.wunderlore.game.InvalidCharacterOptionException;
import com.wunderlore.game.RoundAlreadyAnsweredException;
import com.wunderlore.game.RoundCapacityExceededException;
import com.wunderlore.game.RoundNotFoundException;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@RestController
@RequestMapping("/api/game")
public class GameController {

        private static final Logger log = LoggerFactory.getLogger(GameController.class);

        // Also the detail LoreUnavailableAdvice sends for lore failures raised by this
        // controller.
        static final String ROUND_UNAVAILABLE_DETAIL = "Unable to generate a game round right now";

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

        // Lore and upstream failures are handled by LoreUnavailableAdvice; this one is
        // specific
        // to rounds: WhoSaidItService raises it when the loaded data cannot form a
        // round.
        @ExceptionHandler(InsufficientGameDataException.class)
        public ProblemDetail handleInsufficientGameData(InsufficientGameDataException exception) {
                // Not logged anywhere else, and raised with fixed messages.
                log.warn("Game round generation failed: {}: {}",
                                exception.getClass().getName(), exception.getMessage());

                return ProblemDetail.forStatusAndDetail(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                ROUND_UNAVAILABLE_DETAIL);
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
