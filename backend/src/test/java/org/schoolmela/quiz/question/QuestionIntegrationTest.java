package org.schoolmela.quiz.question;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.schoolmela.quiz.IntegrationTest;
import org.springframework.test.web.servlet.ResultActions;

class QuestionIntegrationTest extends IntegrationTest {

    static String questionJson(String text, int timeLimitSeconds) {
        return """
                {"text": "%s", "options": ["Delhi", "Mumbai", "Kolkata", "Chennai"],
                 "correctOption": 0, "timeLimitSeconds": %d}
                """.formatted(text, timeLimitSeconds);
    }

    private ResultActions createQuestion(String text) throws Exception {
        return asAdmin(post("/admin/questions"), questionJson(text, 45));
    }

    @Test
    void createsAndReadsAQuestion() throws Exception {
        long id = idOf(asAdmin(post("/admin/questions"), """
                {"text": "  What is the capital of India?  ", "options": [" Delhi ", "Mumbai", "Kolkata", "Chennai"],
                 "correctOption": 0, "timeLimitSeconds": 45}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("What is the capital of India?"))
                .andExpect(jsonPath("$.options[0]").value("Delhi")));

        asAdmin(get("/admin/questions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options").value(contains("Delhi", "Mumbai", "Kolkata", "Chennai")))
                .andExpect(jsonPath("$.correctOption").value(0))
                .andExpect(jsonPath("$.timeLimitSeconds").value(45));
    }

    @Test
    void searchesTheQuestionBankByText() throws Exception {
        String marker = UUID.randomUUID().toString();
        createQuestion("Find me " + marker).andExpect(status().isCreated());

        asAdmin(get("/admin/questions").param("q", marker.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].text").value("Find me " + marker));
    }

    @Test
    void updatesAQuestion() throws Exception {
        long id = idOf(createQuestion("Old text"));

        asAdmin(put("/admin/questions/{id}", id), """
                {"text": "New text", "options": ["1", "2", "3", "4"], "correctOption": 3, "timeLimitSeconds": 120}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("New text"))
                .andExpect(jsonPath("$.correctOption").value(3));
    }

    @Test
    void explainsInvalidQuestions() throws Exception {
        asAdmin(post("/admin/questions"), """
                {"text": "", "options": ["A", "B", "C"], "correctOption": 4, "timeLimitSeconds": 10}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.text").value("Please type the question"))
                .andExpect(jsonPath("$.errors.options").value("Please fill in all 4 answers"))
                .andExpect(jsonPath("$.errors.correctOption").value("Please choose the correct answer"))
                .andExpect(jsonPath("$.errors.timeLimitSeconds").value("Time must be 30 to 120 seconds"));

        asAdmin(post("/admin/questions"), """
                {"text": "Q", "options": ["A", " ", "C", "D"], "correctOption": 0, "timeLimitSeconds": 30}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['options[1]']").value("Please fill in this answer"));

        asAdmin(post("/admin/questions"), questionJson("x".repeat(501), 30))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.text").value("The question must be 500 characters or less"));
    }

    @Test
    void rejectsRepeatedAnswers() throws Exception {
        asAdmin(post("/admin/questions"), """
                {"text": "Q", "options": ["Cat", "Dog", "cat ", "Cow"], "correctOption": 0, "timeLimitSeconds": 30}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors['options[2]']").value("Each answer must be different"));
    }

    @Test
    void deletedQuestionsDisappear() throws Exception {
        long id = idOf(createQuestion("Delete me"));

        asAdmin(delete("/admin/questions/{id}", id)).andExpect(status().isNoContent());

        asAdmin(get("/admin/questions/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"));
        asAdmin(put("/admin/questions/{id}", id), questionJson("Back?", 30)).andExpect(status().isNotFound());
    }

    @Test
    void cannotDeleteAQuestionThatAQuizUses() throws Exception {
        long id = idOf(createQuestion("In use"));
        asAdmin(post("/admin/quizzes"), """
                {"title": "Geography Test", "showAnswers": true, "questionIds": [%d]}
                """.formatted(id)).andExpect(status().isCreated());

        asAdmin(delete("/admin/questions/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUESTION_IN_USE"))
                .andExpect(jsonPath("$.detail").value(
                        "This question is used in the quiz \"Geography Test\". Remove it from the quiz first."));
    }

    @Test
    void studentsCannotManageQuestions() throws Exception {
        String mobile = uniqueMobile();
        register("Asha", mobile, "4321").andExpect(status().isCreated());

        mvc.perform(get("/admin/questions").header("Authorization", bearer(accessTokenFor(mobile, "4321"))))
                .andExpect(status().isForbidden());
    }
}
