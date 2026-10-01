package org.schoolmela.quiz.assignment;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.schoolmela.quiz.IntegrationTest;
import org.springframework.test.web.servlet.ResultActions;

class AssignmentIntegrationTest extends IntegrationTest {

    /** A registered student: their id and an access token. */
    private record Student(long id, String token) {
    }

    private Student student(String name) throws Exception {
        String body = register(name, uniqueMobile(), "4321").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Student(((Number) JsonPath.read(body, "$.user.id")).longValue(), JsonPath.read(body, "$.accessToken"));
    }

    private long quiz(String title) throws Exception {
        long q1 = idOf(asAdmin(post("/admin/questions"), """
                {"text": "Q for %s", "options": ["A", "B", "C", "D"], "correctOption": 0, "timeLimitSeconds": 30}
                """.formatted(title)));
        long q2 = idOf(asAdmin(post("/admin/questions"), """
                {"text": "Q2 for %s", "options": ["A", "B", "C", "D"], "correctOption": 0, "timeLimitSeconds": 60}
                """.formatted(title)));
        return idOf(asAdmin(post("/admin/quizzes"), """
                {"title": "%s", "totalTimeLimitSeconds": null, "showAnswers": true, "questionIds": [%d, %d]}
                """.formatted(title, q1, q2)));
    }

    private long group(long... studentIds) throws Exception {
        long id = idOf(asAdmin(post("/admin/groups"), "{\"name\": \"Group " + UUID.randomUUID() + "\"}"));
        StringBuilder ids = new StringBuilder();
        for (long s : studentIds) {
            ids.append(ids.isEmpty() ? "" : ",").append(s);
        }
        if (studentIds.length > 0) {
            asAdmin(post("/admin/groups/{id}/members", id), "{\"studentIds\": [" + ids + "]}").andExpect(status().isOk());
        }
        return id;
    }

    private ResultActions assign(long quizId, String json) throws Exception {
        return asAdmin(post("/admin/quizzes/{quizId}/assignments", quizId), json);
    }

    private ResultActions myQuizzes(Student student) throws Exception {
        return mvc.perform(get("/me/quizzes").header("Authorization", bearer(student.token())));
    }

    private static String title(String prefix) {
        return prefix + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void aQuizGivenToOneStudentShowsOnlyOnTheirDashboard() throws Exception {
        Student asha = student("Asha");
        Student ravi = student("Ravi");
        String title = title("Direct");
        long quizId = quiz(title);
        Instant due = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

        assign(quizId, """
                {"targetType": "STUDENT", "studentId": %d, "dueAt": "%s"}
                """.formatted(asha.id(), due))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentName").value("Asha"));

        myQuizzes(asha)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == '%s')].questionCount".formatted(title)).value(contains(2)))
                .andExpect(jsonPath("$[?(@.title == '%s')].questionTimeSeconds".formatted(title)).value(contains(90)))
                .andExpect(jsonPath("$[?(@.title == '%s')].dueAt".formatted(title)).value(contains(due.toString())))
                .andExpect(jsonPath("$[?(@.title == '%s')].status".formatted(title)).value(contains("NEW")))
                .andExpect(jsonPath("$[0].questions").doesNotExist());
        myQuizzes(ravi).andExpect(jsonPath("$[*].title").value(not(hasItem(title))));
    }

    @Test
    void groupAssignmentsFollowGroupMembership() throws Exception {
        Student member = student("Member");
        Student later = student("Later");
        Student outsider = student("Outsider");
        long groupId = group(member.id());
        String title = title("Group");
        assign(quiz(title), "{\"targetType\": \"GROUP\", \"groupId\": %d}".formatted(groupId))
                .andExpect(status().isCreated());

        myQuizzes(member).andExpect(jsonPath("$[*].title").value(hasItem(title)));
        myQuizzes(outsider).andExpect(jsonPath("$[*].title").value(not(hasItem(title))));

        // Joining the group later still brings the quiz; leaving it takes the quiz away.
        asAdmin(post("/admin/groups/{id}/members", groupId), "{\"studentIds\": [%d]}".formatted(later.id()));
        myQuizzes(later).andExpect(jsonPath("$[*].title").value(hasItem(title)));
        asAdmin(delete("/admin/groups/{id}/members/{studentId}", groupId, member.id()));
        myQuizzes(member).andExpect(jsonPath("$[*].title").value(not(hasItem(title))));
    }

    @Test
    void anAssignmentToEveryoneReachesStudentsWhoRegisterLater() throws Exception {
        String title = title("Everyone");
        assign(quiz(title), "{\"targetType\": \"ALL\"}").andExpect(status().isCreated());

        Student newcomer = student("Newcomer");

        myQuizzes(newcomer).andExpect(jsonPath("$[*].title").value(hasItem(title)));
    }

    @Test
    void aQuizGivenSeveralWaysIsListedOnceWithTheMostGenerousDueDate() throws Exception {
        Student asha = student("Asha");
        long groupId = group(asha.id());
        String title = title("Twice");
        long quizId = quiz(title);
        assign(quizId, """
                {"targetType": "STUDENT", "studentId": %d, "dueAt": "%s"}
                """.formatted(asha.id(), Instant.now().plus(1, ChronoUnit.DAYS)));
        assign(quizId, "{\"targetType\": \"GROUP\", \"groupId\": %d}".formatted(groupId));

        myQuizzes(asha)
                .andExpect(jsonPath("$[?(@.title == '%s')]".formatted(title)).value(org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[?(@.title == '%s')].dueAt".formatted(title)).value(contains((Object) null)));
    }

    @Test
    void listsAndRemovesAssignments() throws Exception {
        Student asha = student("Asha");
        long groupId = group();
        String title = title("Listed");
        long quizId = quiz(title);
        assign(quizId, "{\"targetType\": \"STUDENT\", \"studentId\": %d}".formatted(asha.id()));
        assign(quizId, "{\"targetType\": \"GROUP\", \"groupId\": %d}".formatted(groupId));
        long everyone = idOf(assign(quizId, "{\"targetType\": \"ALL\"}"));

        asAdmin(get("/admin/quizzes/{quizId}/assignments", quizId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].targetType").value(contains("ALL", "GROUP", "STUDENT")));

        asAdmin(delete("/admin/quizzes/{quizId}/assignments/{id}", quizId, everyone)).andExpect(status().isNoContent());
        asAdmin(delete("/admin/quizzes/{quizId}/assignments/{id}", quizId, everyone)).andExpect(status().isNotFound());
        asAdmin(get("/admin/quizzes/{quizId}/assignments", quizId))
                .andExpect(jsonPath("$[*].targetType").value(contains("GROUP", "STUDENT")));
    }

    @Test
    void rejectsDuplicateAndInvalidAssignments() throws Exception {
        Student asha = student("Asha");
        long quizId = quiz(title("Rules"));
        assign(quizId, "{\"targetType\": \"STUDENT\", \"studentId\": %d}".formatted(asha.id()))
                .andExpect(status().isCreated());

        assign(quizId, "{\"targetType\": \"STUDENT\", \"studentId\": %d}".formatted(asha.id()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_ASSIGNED"))
                .andExpect(jsonPath("$.detail").value("This quiz is already given to Asha."));
        assign(quizId, "{\"targetType\": \"STUDENT\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.studentId").value("Please choose a student"));
        assign(quizId, "{\"targetType\": \"GROUP\", \"groupId\": 999999}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.groupId").value("Please choose a group"));
        assign(quizId, "{\"targetType\": \"ALL\", \"dueAt\": \"2020-01-01T00:00:00Z\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dueAt").value("The due date has already passed"));
        assign(quizId, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.targetType").value("Please choose who should take the quiz"));
    }

    @Test
    void deletingAQuizTakesItAwayFromStudents() throws Exception {
        Student asha = student("Asha");
        String title = title("Deleted");
        long quizId = quiz(title);
        assign(quizId, "{\"targetType\": \"STUDENT\", \"studentId\": %d}".formatted(asha.id()));

        asAdmin(delete("/admin/quizzes/{id}", quizId)).andExpect(status().isNoContent());

        myQuizzes(asha).andExpect(jsonPath("$[*].title").value(not(hasItem(title))));
    }

    @Test
    void onlyStudentsHaveAQuizDashboardAndOnlyAdminsAssign() throws Exception {
        Student asha = student("Asha");
        long quizId = quiz(title("Roles"));

        mvc.perform(post("/admin/quizzes/{quizId}/assignments", quizId)
                        .header("Authorization", bearer(asha.token()))
                        .contentType("application/json")
                        .content("{\"targetType\": \"ALL\"}"))
                .andExpect(status().isForbidden());
        asAdmin(get("/me/quizzes")).andExpect(status().isForbidden());
    }
}
