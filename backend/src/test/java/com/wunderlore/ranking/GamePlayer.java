package com.wunderlore.ranking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

// Plays each of the four games to the end through the real HTTP API, answering without caring
// whether the answer is right: tests compare the published score with the one the game itself
// reports, so they never need to know the correct answers.
final class GamePlayer {

    record Played(String gameId, int score, int maxScore) {
    }

    private final MockMvc mockMvc;

    GamePlayer(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    Played play(GameType type) throws Exception {
        return play(type, "EASY");
    }

    // difficulty (EASY, MEDIUM or HARD) is ignored by Who Said It?, which has no levels.
    Played play(GameType type, String difficulty) throws Exception {
        return switch (type) {
            case LORE_CHALLENGE -> playLoreChallenge(difficulty);
            case WHO_AM_I -> playWhoAmI(difficulty);
            case WHERE_IN_MIDDLE_EARTH -> playWhereInMiddleEarth(difficulty);
            case WHO_SAID_IT -> playWhoSaidIt();
        };
    }

    // Starts a game and answers part of it, leaving it unfinished.
    String startUnfinished(GameType type) throws Exception {
        return switch (type) {
            case LORE_CHALLENGE -> id(post("/api/games/lore-challenge/matches")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"category\":\"ALL\",\"difficulty\":\"EASY\"}"), "$.matchId");
            case WHO_AM_I -> id(post("/api/games/who-am-i/games")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"difficulty\":\"EASY\"}"), "$.gameId");
            case WHERE_IN_MIDDLE_EARTH -> id(post("/api/games/where-in-middle-earth/games")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"difficulty\":\"EASY\"}"), "$.gameId");
            case WHO_SAID_IT -> id(post("/api/games/who-said-it/matches"), "$.matchId");
        };
    }

    private Played playLoreChallenge(String difficulty) throws Exception {
        String base = "/api/games/lore-challenge/matches";
        String body = send(post(base)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"ALL\",\"difficulty\":\"" + difficulty + "\"}"));
        String matchId = JsonPath.read(body, "$.matchId");
        String questionId = JsonPath.read(body, "$.questionId");
        String optionId = JsonPath.read(body, "$.options[0].id");

        while (true) {
            String answer = send(post(base + "/" + matchId + "/answer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"questionId\":\"" + questionId + "\",\"optionId\":\"" + optionId + "\"}"));

            if ((Boolean) JsonPath.read(answer, "$.finished")) {
                break;
            }

            String next = send(get(base + "/" + matchId + "/question"));

            questionId = JsonPath.read(next, "$.questionId");
            optionId = JsonPath.read(next, "$.options[0].id");
        }

        return result(base, matchId);
    }

    private Played playWhoAmI(String difficulty) throws Exception {
        String base = "/api/games/who-am-i/games";
        String round = send(post(base)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"difficulty\":\"" + difficulty + "\"}"));
        String gameId = JsonPath.read(round, "$.gameId");

        while (true) {
            int roundNumber = JsonPath.read(round, "$.roundNumber");
            int clueNumber = JsonPath.read(round, "$.clueNumber");
            String answer = send(post(base + "/" + gameId + "/answer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"roundNumber\":" + roundNumber + ",\"clueNumber\":" + clueNumber
                            + ",\"optionId\":\"a\"}"));

            if ((Boolean) JsonPath.read(answer, "$.gameFinished")) {
                break;
            }

            round = send(get(base + "/" + gameId + "/round"));
        }

        return result(base, gameId);
    }

    private Played playWhereInMiddleEarth(String difficulty) throws Exception {
        String base = "/api/games/where-in-middle-earth/games";
        String round = send(post(base)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"difficulty\":\"" + difficulty + "\"}"));
        String gameId = JsonPath.read(round, "$.gameId");

        while (true) {
            int roundNumber = JsonPath.read(round, "$.roundNumber");
            String answer = send(post(base + "/" + gameId + "/answer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"roundNumber\":" + roundNumber + ",\"x\":500,\"y\":350}"));

            if ((Boolean) JsonPath.read(answer, "$.gameFinished")) {
                break;
            }

            round = send(get(base + "/" + gameId + "/round"));
        }

        return result(base, gameId);
    }

    private Played playWhoSaidIt() throws Exception {
        String base = "/api/games/who-said-it/matches";
        String question = send(post(base));
        String matchId = JsonPath.read(question, "$.matchId");

        while (true) {
            int questionNumber = JsonPath.read(question, "$.questionNumber");
            String characterId = JsonPath.read(question, "$.options[0].id");
            String answer = send(post(base + "/" + matchId + "/answer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"questionNumber\":" + questionNumber + ",\"characterId\":\"" + characterId + "\"}"));

            if ((Boolean) JsonPath.read(answer, "$.matchFinished")) {
                break;
            }

            question = send(get(base + "/" + matchId + "/question"));
        }

        return result(base, matchId);
    }

    private Played result(String base, String id) throws Exception {
        String result = send(get(base + "/" + id + "/result"));

        return new Played(id, JsonPath.read(result, "$.score"), JsonPath.read(result, "$.maxScore"));
    }

    private String id(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String path)
            throws Exception {
        return JsonPath.read(send(request), path);
    }

    private String send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
