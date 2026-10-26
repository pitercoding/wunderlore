package com.wunderlore.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import com.wunderlore.game.GameRoundResponse;
import com.wunderlore.game.WhoSaidItService;

@RestController
@RequestMapping("/api/game")
public class GameController {

    private final WhoSaidItService whoSaidItService;

    public GameController(WhoSaidItService whoSaidItService) {
        this.whoSaidItService = whoSaidItService;
    }

    @GetMapping("/round")
    public GameRoundResponse getRound() {
        return GameRoundResponse.from(whoSaidItService.generateRound());
    }

    @ExceptionHandler({ RestClientException.class, IllegalStateException.class })
    public ProblemDetail handleRoundUnavailable() {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Unable to generate a game round right now");
    }
}
