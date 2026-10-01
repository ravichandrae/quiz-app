package org.schoolmela.quiz.attempt;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.schoolmela.quiz.IntegrationTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class AttemptIntegrationTest extends IntegrationTest {

    private record Student(long id, String token) {
    }

    private Student student() throws Exception {
        String body = register("Asha", uniqueMobile(), "4321").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Student(((Number) JsonPath.read(body, "$.user.id")).longValue(), JsonPath.read(body, "$.accessToken"));
    }

    private long question(String text, int correct, int seconds) throws Exception {
        return idOf(asAdmin(post("/admin/questions"), """
                {"text": "%s", "options": ["A", "B", "C", "D"], "correctOption": %d, "timeLimitSeconds": %d}
                """.formatted(text, correct, seconds)));
    }

    /** A quiz of three questions (correct options B, C, A; 30 seconds each) given to the student. */
    private long quizFor(Student student, Integer totalSeconds) throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        long q1 = question("First " + tag, 1, 30);
        long q2 = question("Second " + tag, 2, 30);
        long q3 = question("Third " + tag, 0, 30);
        long quizId = idOf(asAdmin(post("/admin/quizzes"), """
                {"title": "Quiz %s", "totalTimeLimitSeconds": %s, "showAnswers": true, "questionIds": [%d, %d, %d]}
                """.formatted(tag, totalSeconds, q1, q2, q3)));
        asAdmin(post("/admin/quizzes/{id}/assignments", quizId),
                "{\"targetType\": \"STUDENT\", \"studentId\": %d}".formatted(student.id()))
                .andExpect(status().isCreated());
        return quizId;
    }

    private ResultActions start(Student s, long quizId) throws Exception {
        return mvc.perform(post("/me/quizzes/{id}/attempt", quizId).header("Authorization", bearer(s.token())));
    }

    private ResultActions answer(Student s, long attemptId, int position, Integer option) throws Exception {
        return mvc.perform(post("/me/attempts/{id}/answers", attemptId)
                .header("Authorization", bearer(s.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"position\": %d, \"selectedOption\": %s}".formatted(position, option)));
    }

    private ResultActions myQuiz(Student s, long quizId) throws Exception {
        return mvc.perform(get("/me/quizzes/{id}", quizId).header("Authorization", bearer(s.token())));
    }

    private static long attemptIdOf(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.attemptId")).longValue();
    }

    @Test
    void takesAQuizFromStartToScore() throws Exception {
        Student asha = student();
        long quizId = quizFor(asha, null);
        myQuiz(asha, quizId).andExpect(jsonPath("$.status").value("NEW"));

        long attemptId = attemptIdOf(start(asha, quizId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.questionCount").value(3))
                .andExpect(jsonPath("$.question.position").value(1))
                .andExpect(jsonPath("$.question.text").value(org.hamcrest.Matchers.startsWith("First")))
                .andExpect(jsonPath("$.question.options").value(contains("A", "B", "C", "D")))
                .andExpect(jsonPath("$.question.secondsLeft").value(30))
                .andExpect(jsonPath("$.question.correctOption").doesNotExist())
                .andExpect(jsonPath("$.quizSecondsLeft").doesNotExist()));
        myQuiz(asha, quizId).andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        answer(asha, attemptId, 1, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question.position").value(2));
        answer(asha, attemptId, 2, 0).andExpect(jsonPath("$.question.position").value(3));
        answer(asha, attemptId, 3, 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.question").doesNotExist())
                .andExpect(jsonPath("$.result.score").value(2))
                .andExpect(jsonPath("$.result.questionCount").value(3))
                .andExpect(jsonPath("$.result.percentage").value(67))
                .andExpect(jsonPath("$.result.finishReason").value("ALL_ANSWERED"));

        myQuiz(asha, quizId)
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.score").value(2));
        start(asha, quizId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_TAKEN"));
    }

    @Test
    void timeRunsOnTheServerNotTheScreen() throws Exception {
        Student asha = student();
        long attemptId = attemptIdOf(start(asha, quizFor(asha, null)));

        clock.advance(Duration.ofSeconds(40));

        // The correct answer arrives after 30 seconds + 3 seconds grace: it does not count.
        answer(asha, attemptId, 1, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question.position").value(2))
                .andExpect(jsonPath("$.question.secondsLeft").value(30));
        answer(asha, attemptId, 2, 2);
        answer(asha, attemptId, 3, 0).andExpect(jsonPath("$.result.score").value(2));
    }

    @Test
    void resumingContinuesWhereTheStudentLeftOff() throws Exception {
        Student asha = student();
        long quizId = quizFor(asha, null);
        long attemptId = attemptIdOf(start(asha, quizId));
        answer(asha, attemptId, 1, 1);

        clock.advance(Duration.ofSeconds(10));

        start(asha, quizId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptId").value(attemptId))
                .andExpect(jsonPath("$.question.position").value(2))
                .andExpect(jsonPath("$.question.secondsLeft").value(20));
        mvc.perform(get("/me/attempts/{id}", attemptId).header("Authorization", bearer(asha.token())))
                .andExpect(jsonPath("$.question.position").value(2));
    }

    @Test
    void theWholeQuizEndsWhenItsTimeLimitIsReached() throws Exception {
        Student asha = student();
        long quizId = quizFor(asha, 60);
        long attemptId = attemptIdOf(start(asha, quizId)
                .andExpect(jsonPath("$.quizSecondsLeft").value(60)));
        answer(asha, attemptId, 1, 1);

        clock.advance(Duration.ofSeconds(70));

        mvc.perform(get("/me/attempts/{id}", attemptId).header("Authorization", bearer(asha.token())))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.score").value(1))
                .andExpect(jsonPath("$.result.finishReason").value("TIME_UP"));
    }

    @Test
    void anAbandonedQuizShowsAsDoneOnTheDashboardOnceItsTimeIsOver() throws Exception {
        Student asha = student();
        long quizId = quizFor(asha, 60);
        start(asha, quizId);

        clock.advance(Duration.ofMinutes(5));

        myQuiz(asha, quizId)
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.score").value(0));
    }

    @Test
    void editingTheQuizDoesNotChangeAStartedAttempt() throws Exception {
        Student asha = student();
        long quizId = quizFor(asha, null);
        long attemptId = attemptIdOf(start(asha, quizId));
        long other = question("Replacement", 3, 30);

        asAdmin(put("/admin/quizzes/{id}", quizId), """
                {"title": "Changed", "totalTimeLimitSeconds": null, "showAnswers": true, "questionIds": [%d]}
                """.formatted(other)).andExpect(status().isOk());

        answer(asha, attemptId, 1, 1)
                .andExpect(jsonPath("$.quizTitle").value(org.hamcrest.Matchers.startsWith("Quiz ")))
                .andExpect(jsonPath("$.questionCount").value(3))
                .andExpect(jsonPath("$.question.text").value(org.hamcrest.Matchers.startsWith("Second")));
    }

    @Test
    void cannotSkipAhead() throws Exception {
        Student asha = student();
        long attemptId = attemptIdOf(start(asha, quizFor(asha, null)));

        answer(asha, attemptId, 2, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_OPEN"));
        answer(asha, attemptId, 1, 7).andExpect(status().isBadRequest());
    }

    @Test
    void studentsCanOnlyTakeTheirOwnAssignedQuizzes() throws Exception {
        Student asha = student();
        Student ravi = student();
        long quizId = quizFor(asha, null);

        start(ravi, quizId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUIZ_NOT_AVAILABLE"));
        long attemptId = attemptIdOf(start(asha, quizId));
        mvc.perform(get("/me/attempts/{id}", attemptId).header("Authorization", bearer(ravi.token())))
                .andExpect(status().isNotFound());
        answer(ravi, attemptId, 1, 1).andExpect(status().isNotFound());
        asAdmin(post("/me/quizzes/{id}/attempt", quizId)).andExpect(status().isForbidden());
    }
}
