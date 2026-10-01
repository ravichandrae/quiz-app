package org.schoolmela.quiz.quiz;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.schoolmela.quiz.IntegrationTest;

class QuizIntegrationTest extends IntegrationTest {

    private long question(String text, int seconds) throws Exception {
        return idOf(asAdmin(post("/admin/questions"), """
                {"text": "%s", "options": ["A", "B", "C", "D"], "correctOption": 1, "timeLimitSeconds": %d}
                """.formatted(text, seconds)).andExpect(status().isCreated()));
    }

    private static String quizJson(String title, Integer totalSeconds, long... questionIds) {
        StringBuilder ids = new StringBuilder();
        for (long id : questionIds) {
            ids.append(ids.isEmpty() ? "" : ",").append(id);
        }
        return """
                {"title": "%s", "totalTimeLimitSeconds": %s, "showAnswers": false, "questionIds": [%s]}
                """.formatted(title, totalSeconds, ids);
    }

    @Test
    void createsAQuizWithQuestionsInOrder() throws Exception {
        long q1 = question("First", 30);
        long q2 = question("Second", 60);
        long q3 = question("Third", 90);

        long id = idOf(asAdmin(post("/admin/quizzes"), quizJson("Science Week 1", 600, q3, q1, q2))
                .andExpect(status().isCreated()));

        asAdmin(get("/admin/quizzes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Science Week 1"))
                .andExpect(jsonPath("$.totalTimeLimitSeconds").value(600))
                .andExpect(jsonPath("$.showAnswers").value(false))
                .andExpect(jsonPath("$.questionTimeSeconds").value(180))
                .andExpect(jsonPath("$.questions[*].text").value(contains("Third", "First", "Second")))
                .andExpect(jsonPath("$.questions[0].correctOption").value(1));
    }

    @Test
    void reordersAndReplacesQuestions() throws Exception {
        long q1 = question("One", 30);
        long q2 = question("Two", 30);
        long q3 = question("Three", 30);
        long id = idOf(asAdmin(post("/admin/quizzes"), quizJson("Maths", null, q1, q2)));

        asAdmin(put("/admin/quizzes/{id}", id), quizJson("Maths revised", null, q2, q1, q3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Maths revised"))
                .andExpect(jsonPath("$.totalTimeLimitSeconds").value(nullValue()))
                .andExpect(jsonPath("$.questions[*].text").value(contains("Two", "One", "Three")));

        asAdmin(put("/admin/quizzes/{id}", id), quizJson("Maths short", null, q3))
                .andExpect(status().isOk());
        asAdmin(get("/admin/quizzes/{id}", id))
                .andExpect(jsonPath("$.questions[*].text").value(contains("Three")));
    }

    @Test
    void listsQuizzesWithQuestionCountsAndTimes() throws Exception {
        String marker = UUID.randomUUID().toString();
        long q1 = question("A", 45);
        long q2 = question("B", 75);
        asAdmin(post("/admin/quizzes"), quizJson("Listed " + marker, null, q1, q2)).andExpect(status().isCreated());

        asAdmin(get("/admin/quizzes").param("q", marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].questionCount").value(2))
                .andExpect(jsonPath("$.content[0].questionTimeSeconds").value(120))
                .andExpect(jsonPath("$.content[0].questions").doesNotExist());
    }

    @Test
    void rejectsInvalidQuizzes() throws Exception {
        long q1 = question("Only", 30);

        asAdmin(post("/admin/quizzes"), quizJson(" ", 30))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").value("Please give the quiz a title"))
                .andExpect(jsonPath("$.errors.totalTimeLimitSeconds").value("The quiz time must be at least 1 minute"))
                .andExpect(jsonPath("$.errors.questionIds").value("A quiz needs 1 to 100 questions"));

        asAdmin(post("/admin/quizzes"), quizJson("Twice", null, q1, q1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.questionIds").value("A question was added more than once"));

        asAdmin(post("/admin/quizzes"), quizJson("Missing", null, q1, 987_654L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.questionIds").value(
                        "Some questions were deleted from the question bank. Remove them and try again."));
    }

    @Test
    void deletedQuizzesDisappearAndFreeTheirQuestions() throws Exception {
        long q1 = question("Freed", 30);
        long id = idOf(asAdmin(post("/admin/quizzes"), quizJson("Temporary", null, q1)));
        asAdmin(delete("/admin/questions/{id}", q1)).andExpect(status().isConflict());

        asAdmin(delete("/admin/quizzes/{id}", id)).andExpect(status().isNoContent());

        asAdmin(get("/admin/quizzes/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUIZ_NOT_FOUND"));
        asAdmin(delete("/admin/questions/{id}", q1)).andExpect(status().isNoContent());
    }
}
