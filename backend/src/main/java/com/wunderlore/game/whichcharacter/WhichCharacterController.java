package com.wunderlore.game.whichcharacter;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wunderlore.game.whichcharacter.dto.QuizQuestionsResponse;
import com.wunderlore.game.whichcharacter.dto.QuizResultRequest;
import com.wunderlore.game.whichcharacter.dto.QuizResultResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/games/which-character")
public class WhichCharacterController {

    static final String INVALID_ANSWERS_DETAIL = "Invalid quiz answers";

    private final WhichCharacterScoringService scoringService;

    public WhichCharacterController(WhichCharacterScoringService scoringService) {
        this.scoringService = scoringService;
    }

    @GetMapping("/questions")
    public QuizQuestionsResponse questions() {
        return QuizQuestionsResponse.from(WhichCharacterQuiz.QUESTIONS);
    }

    @PostMapping("/result")
    public QuizResultResponse result(@Valid @RequestBody QuizResultRequest request) {
        return QuizResultResponse.from(scoringService.calculateResult(request.toQuizAnswers()));
    }

    // Only the dedicated exception is mapped: any other IllegalArgumentException is a bug
    // and must stay a 500. The generic detail never echoes submitted IDs or exception text.
    @ExceptionHandler({
            WhichCharacterInvalidAnswersException.class,
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class
    })
    public ProblemDetail handleInvalidAnswers(Exception exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, INVALID_ANSWERS_DETAIL);
    }
}
