package com.wunderlore.game.whichcharacter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Collection;
import java.util.TreeSet;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// Checks the public HTTP contract: the body the controller really writes, through
// the application's configured JsonMapper, against the fixtures shared with the
// frontend (contracts/which-character).
@WebMvcTest(WhichCharacterController.class)
class WhichCharacterContractTest {

    // Maven runs the tests from the backend directory.
    private static final Path CONTRACTS = Path.of("..", "contracts", "which-character");

    private static final String QUESTIONS_PATH = "/api/games/which-character/questions";
    private static final String RESULT_PATH = "/api/games/which-character/result";

    // Required by LoreUnavailableAdvice, which the web slice loads with the controller.
    @TestConfiguration
    static class ClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private WhichCharacterScoringService scoringService;

    @Test
    void questionsMatchFixture() throws Exception {
        String raw = mockMvc.perform(get(QUESTIONS_PATH)).andReturn().getResponse().getContentAsString();
        JsonNode body = jsonMapper.readTree(raw);

        assertThat(body).isEqualTo(fixture("questions.json"));
        // Explicit, so a failure names the property that changed.
        assertThat(fieldNames(body)).containsExactly("questions");
        assertThat(body.get("questions").isArray()).isTrue();
        assertThat(body.get("questions")).hasSize(10);
        for (JsonNode question : body.get("questions")) {
            assertThat(fieldNames(question)).containsExactly("id", "options", "text");
            assertThat(question.get("id").isString()).isTrue();
            assertThat(question.get("text").isString()).isTrue();
            assertThat(question.get("options").isArray()).isTrue();
            assertThat(question.get("options")).hasSize(4);
            for (JsonNode option : question.get("options")) {
                assertThat(fieldNames(option)).containsExactly("id", "text");
                assertThat(option.get("id").isString()).isTrue();
                assertThat(option.get("text").isString()).isTrue();
            }
        }
        // Scoring and character association stay server-side.
        assertThat(raw).doesNotContain("primaryCharacter", "secondaryCharacter", "score");
    }

    @Test
    void resultMatchesFixture() throws Exception {
        when(scoringService.calculateResult(any()))
                .thenReturn(WhichCharacterQuiz.CHARACTERS.get(CharacterId.GANDALF));

        String raw = mockMvc.perform(post(RESULT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()))
                .andReturn().getResponse().getContentAsString();
        JsonNode body = jsonMapper.readTree(raw);

        assertThat(body).isEqualTo(fixture("result.json"));
        assertThat(fieldNames(body)).containsExactly("character");
        JsonNode character = body.get("character");
        assertThat(fieldNames(character)).containsExactly("description", "id", "name");
        assertThat(character.get("id").isString()).isTrue();
        assertThat(character.get("name").isString()).isTrue();
        assertThat(character.get("description").isString()).isTrue();
        assertThat(raw).doesNotContain("score");
    }

    private static String validBody() {
        String answers = String.join(",", IntStream.rangeClosed(1, 10)
                .mapToObj(i -> String.format("{\"questionId\":\"q%02d\",\"answerId\":\"q%02d_a\"}", i, i))
                .toList());
        return "{\"answers\":[" + answers + "]}";
    }

    private JsonNode fixture(String name) throws IOException {
        return jsonMapper.readTree(Files.readString(CONTRACTS.resolve(name)));
    }

    // Sorted, so the check does not depend on property order.
    private static Collection<String> fieldNames(JsonNode node) {
        return new TreeSet<>(node.propertyNames());
    }
}
