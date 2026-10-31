package com.wunderlore.game.whosaidit;


import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games/who-said-it")
public class WhoSaidItController {

        private static final Logger log = LoggerFactory.getLogger(WhoSaidItController.class);

        // Also the detail LoreUnavailableAdvice sends for lore failures raised by this
        // controller.
        public static final String ROUND_UNAVAILABLE_DETAIL = "Unable to generate a game round right now";

        private final WhoSaidItService whoSaidItService;
        private final WhoSaidItRoundRegistry roundRegistry;

        public WhoSaidItController(
                        WhoSaidItService whoSaidItService,
                        WhoSaidItRoundRegistry roundRegistry) {
                this.whoSaidItService = whoSaidItService;
                this.roundRegistry = roundRegistry;
        }

        @PostMapping("/rounds")
        public WhoSaidItRoundResponse createRound() {
                WhoSaidItRound round = whoSaidItService.generateRound();
                String roundId = roundRegistry.register(round);

                return WhoSaidItRoundResponse.from(roundId, round);
        }

        @PostMapping("/rounds/{roundId}/answer")
        public WhoSaidItAnswerResponse answer(
                        @PathVariable String roundId,
                        @Valid @RequestBody WhoSaidItAnswerRequest request) {
                return roundRegistry.answer(roundId, request.characterId());
        }

        // Lore and upstream failures are handled by LoreUnavailableAdvice; this one is
        // specific
        // to rounds: WhoSaidItService raises it when the loaded data cannot form a
        // round.
        @ExceptionHandler(WhoSaidItInsufficientDataException.class)
        public ProblemDetail handleInsufficientData(WhoSaidItInsufficientDataException exception) {
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

        @ExceptionHandler(WhoSaidItInvalidCharacterOptionException.class)
        public ProblemDetail handleInvalidCharacterOption(
                        WhoSaidItInvalidCharacterOptionException exception) {
                return ProblemDetail.forStatusAndDetail(
                                HttpStatus.BAD_REQUEST,
                                exception.getMessage());
        }

        @ExceptionHandler(WhoSaidItRoundNotFoundException.class)
        public ProblemDetail handleRoundNotFound(
                        WhoSaidItRoundNotFoundException exception) {
                return ProblemDetail.forStatusAndDetail(
                                HttpStatus.NOT_FOUND,
                                exception.getMessage());
        }

        @ExceptionHandler(WhoSaidItRoundAlreadyAnsweredException.class)
        public ProblemDetail handleRoundAlreadyAnswered(
                        WhoSaidItRoundAlreadyAnsweredException exception) {
                return ProblemDetail.forStatusAndDetail(
                                HttpStatus.CONFLICT,
                                exception.getMessage());
        }

        @ExceptionHandler(WhoSaidItRoundCapacityExceededException.class)
        public ProblemDetail handleRoundCapacityExceeded(
                        WhoSaidItRoundCapacityExceededException exception) {
                return ProblemDetail.forStatusAndDetail(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "Too many active rounds, try again later");
        }
}
