package org.schoolmela.quiz.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.schoolmela.quiz.IntegrationTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ResultIntegrationTest extends IntegrationTest {

    private record Student(long id, String mobile, String token) {
    }

    private Student student(String name) throws Exception {
        String mobile = uniqueMobile();
        String body = register(name, mobile, "4321").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Student(((Number) JsonPath.read(body, "$.user.id")).longValue(), mobile,
                JsonPath.read(body, "$.accessToken"));
    }

    /** Three 30-second questions whose correct options are B, C and A. */
    private long quiz(String title, boolean showAnswers, Student... students) throws Exception {
        long[] ids = new long[3];
        int[] correct = {1, 2, 0};
        for (int i = 0; i < 3; i++) {
            ids[i] = idOf(asAdmin(post("/admin/questions"), """
                    {"text": "Q%d of %s", "options": ["A", "B", "C", "D"], "correctOption": %d, "timeLimitSeconds": 30}
                    """.formatted(i + 1, title, correct[i])));
        }
        long quizId = idOf(asAdmin(post("/admin/quizzes"), """
                {"title": "%s", "totalTimeLimitSeconds": null, "showAnswers": %s, "questionIds": [%d, %d, %d]}
                """.formatted(title, showAnswers, ids[0], ids[1], ids[2])));
        for (Student s : students) {
            asAdmin(post("/admin/quizzes/{id}/assignments", quizId),
                    "{\"targetType\": \"STUDENT\", \"studentId\": %d}".formatted(s.id()));
        }
        return quizId;
    }

    private long start(Student s, long quizId) throws Exception {
        String body = mvc.perform(post("/me/quizzes/{id}/attempt", quizId).header("Authorization", bearer(s.token())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.attemptId")).longValue();
    }

    private void answer(Student s, long attemptId, int position, Integer option) throws Exception {
        mvc.perform(post("/me/attempts/{id}/answers", attemptId)
                        .header("Authorization", bearer(s.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"position\": %d, \"selectedOption\": %s}".formatted(position, option)))
                .andExpect(status().isOk());
    }

    /** Right, wrong, then lets the third question's time run out: score 1 of 3. */
    private long takeQuiz(Student s, long quizId) throws Exception {
        long attemptId = start(s, quizId);
        clock.advance(Duration.ofSeconds(4));
        answer(s, attemptId, 1, 1);
        answer(s, attemptId, 2, 0);
        clock.advance(Duration.ofSeconds(40));
        mvc.perform(get("/me/attempts/{id}", attemptId).header("Authorization", bearer(s.token())))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        return attemptId;
    }

    private ResultActions asStudent(Student s, String url, Object... vars) throws Exception {
        return mvc.perform(get(url, vars).header("Authorization", bearer(s.token())));
    }

    private static String title(String prefix) {
        return prefix + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void studentsReviewTheirAnswersWhenTheQuizShowsThem() throws Exception {
        Student asha = student("Asha");
        long attemptId = takeQuiz(asha, quiz(title("Shown"), true, asha));

        asStudent(asha, "/me/results/{id}", attemptId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(1))
                .andExpect(jsonPath("$.percentage").value(33))
                .andExpect(jsonPath("$.answersShown").value(true))
                .andExpect(jsonPath("$.questions[*].outcome").value(contains("CORRECT", "WRONG", "NO_ANSWER")))
                .andExpect(jsonPath("$.questions[1].selectedOption").value(0))
                .andExpect(jsonPath("$.questions[1].correctOption").value(2));
    }

    @Test
    void onlyTheScoreIsShownWhenTheQuizHidesAnswers() throws Exception {
        Student asha = student("Asha");
        long attemptId = takeQuiz(asha, quiz(title("Hidden"), false, asha));

        asStudent(asha, "/me/results/{id}", attemptId)
                .andExpect(jsonPath("$.score").value(1))
                .andExpect(jsonPath("$.answersShown").value(false))
                .andExpect(jsonPath("$.questions").isEmpty());
    }

    @Test
    void resultsAreOnlyForTheStudentAndOnlyOnceFinished() throws Exception {
        Student asha = student("Asha");
        Student ravi = student("Ravi");
        long attemptId = start(asha, quiz(title("Private"), true, asha));

        asStudent(asha, "/me/results/{id}", attemptId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_FINISHED"));
        asStudent(ravi, "/me/results/{id}", attemptId).andExpect(status().isNotFound());
    }

    @Test
    void theScoreHistoryKeepsQuizzesThatAreNoLongerAssigned() throws Exception {
        Student asha = student("Asha");
        String kept = title("Kept");
        long quizId = quiz(kept, true, asha);
        takeQuiz(asha, quizId);
        String unfinished = title("Unfinished");
        start(asha, quiz(unfinished, true, asha));

        asAdmin(delete("/admin/quizzes/{id}", quizId)).andExpect(status().isNoContent());

        asStudent(asha, "/me/results")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].quizTitle").value(contains(kept)))
                .andExpect(jsonPath("$[0].score").value(1))
                .andExpect(jsonPath("$[0].questionCount").value(3));
    }

    @Test
    void adminsListAttemptsByQuizAndStudent() throws Exception {
        Student asha = student("Asha Results");
        Student ravi = student("Ravi Results");
        long quizId = quiz(title("Listed"), true, asha, ravi);
        takeQuiz(asha, quizId);
        start(ravi, quizId);

        asAdmin(get("/admin/attempts").param("quizId", String.valueOf(quizId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].studentName").value(contains("Ravi Results", "Asha Results")))
                .andExpect(jsonPath("$.content[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.content[1].status").value("COMPLETED"))
                .andExpect(jsonPath("$.content[1].score").value(1))
                .andExpect(jsonPath("$.content[1].percentage").value(33))
                .andExpect(jsonPath("$.content[1].late").value(false));

        asAdmin(get("/admin/attempts").param("quizId", String.valueOf(quizId)).param("q", asha.mobile()))
                .andExpect(jsonPath("$.content[*].studentName").value(contains("Asha Results")));
        asAdmin(get("/admin/attempts").param("q", "ravi results"))
                .andExpect(jsonPath("$.content[*].studentName").value(hasItem("Ravi Results")))
                .andExpect(jsonPath("$.content[*].studentName").value(not(hasItem("Asha Results"))));
    }

    @Test
    void adminsSeeEachAnswerWithTheTimeTaken() throws Exception {
        Student asha = student("Asha");
        long attemptId = takeQuiz(asha, quiz(title("Detail"), false, asha));

        asAdmin(get("/admin/attempts/{id}", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempt.studentName").value("Asha"))
                .andExpect(jsonPath("$.attempt.score").value(1))
                .andExpect(jsonPath("$.finishReason").value("ALL_ANSWERED"))
                // Teachers see correct answers even when students do not.
                .andExpect(jsonPath("$.questions[*].correctOption").value(contains(1, 2, 0)))
                .andExpect(jsonPath("$.questions[*].outcome").value(contains("CORRECT", "WRONG", "NO_ANSWER")))
                .andExpect(jsonPath("$.questions[0].secondsTaken").value(4))
                .andExpect(jsonPath("$.questions[2].secondsTaken").value(30));
    }

    @Test
    void exportsResultsAsCsvThatSpreadsheetsCannotRunAsFormulas() throws Exception {
        Student sneaky = student("=HYPERLINK(\\\"http://x\\\")");
        String title = title("Export");
        long quizId = quiz(title, true, sneaky);
        takeQuiz(sneaky, quizId);

        String csv = asAdmin(get("/admin/attempts/export").param("quizId", String.valueOf(quizId)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"quiz-results.csv\""))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String[] lines = csv.split("\r\n");
        assertThat(lines[0]).startsWith("﻿\"Student name\",\"Mobile number\"");
        assertThat(lines).hasSize(2);
        assertThat(lines[1]).startsWith("\"'=HYPERLINK(\"\"http://x\"\")\",\"" + sneaky.mobile() + "\",,\"" + title
                + "\",\"Completed\",\"1\",\"3\",\"33%\"");
        assertThat(lines[1]).endsWith(",\"No\"");
    }

    @Test
    void studentsCannotSeeOtherPeoplesResults() throws Exception {
        Student asha = student("Asha");
        asStudent(asha, "/admin/attempts").andExpect(status().isForbidden());
        asStudent(asha, "/admin/attempts/export").andExpect(status().isForbidden());
    }

    @Test
    void csvCellsAreQuotedAndFormulasNeutralised() {
        assertThat(ResultService.csvCell(null)).isEmpty();
        assertThat(ResultService.csvCell("Asha")).isEqualTo("\"Asha\"");
        assertThat(ResultService.csvCell("say \"hi\", ok")).isEqualTo("\"say \"\"hi\"\", ok\"");
        assertThat(ResultService.csvCell("+91")).isEqualTo("\"'+91\"");
        assertThat(ResultService.csvCell("@SUM(A1)")).isEqualTo("\"'@SUM(A1)\"");
    }
}
