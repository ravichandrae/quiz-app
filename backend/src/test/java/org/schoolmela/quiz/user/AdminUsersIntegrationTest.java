package org.schoolmela.quiz.user;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.schoolmela.quiz.IntegrationTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class AdminUsersIntegrationTest extends IntegrationTest {

    @Test
    void bootstrapAdminCanLogIn() throws Exception {
        login(ADMIN_MOBILE, ADMIN_PIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.name").value("Head Teacher"));
    }

    @Test
    void studentsCannotUseAdminEndpoints() throws Exception {
        String mobile = uniqueMobile();
        register("Asha", mobile, "4321").andExpect(status().isCreated());
        String studentToken = accessTokenFor(mobile, "4321");

        mvc.perform(get("/admin/users").header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminSearchesStudentsByNameOrMobile() throws Exception {
        String mobile = uniqueMobile();
        register("Zubeida Unique", mobile, "4321").andExpect(status().isCreated());
        String admin = accessTokenFor(ADMIN_MOBILE, ADMIN_PIN);

        mvc.perform(get("/admin/users").param("q", "zubeida").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].mobile").value(mobile))
                .andExpect(jsonPath("$.content[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.content[0].pinHash").doesNotExist());
        mvc.perform(get("/admin/users").param("q", mobile).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.content[0].name").value("Zubeida Unique"));
        // Admins are not listed as students.
        mvc.perform(get("/admin/users").param("q", ADMIN_MOBILE).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void rejectsOversizedPages() throws Exception {
        String admin = accessTokenFor(ADMIN_MOBILE, ADMIN_PIN);
        mvc.perform(get("/admin/users").param("size", "500").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deactivatedStudentsCannotLogInOrRefreshUntilReactivated() throws Exception {
        String mobile = uniqueMobile();
        String registered = register("Asha", mobile, "4321").andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(registered, "$.user.id")).longValue();
        String refresh = JsonPath.read(registered, "$.refreshToken");
        String admin = accessTokenFor(ADMIN_MOBILE, ADMIN_PIN);

        setActive(admin, id, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        login(mobile, "4321")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        postJson("/auth/refresh", "{\"refreshToken\": \"" + refresh + "\"}")
                .andExpect(status().isUnauthorized());

        setActive(admin, id, true).andExpect(jsonPath("$.active").value(true));
        login(mobile, "4321").andExpect(status().isOk());
    }

    @Test
    void turningAnAccountOffStopsItsCurrentLoginAtOnce() throws Exception {
        String registered = register("Asha", uniqueMobile(), "4321").andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(registered, "$.user.id")).longValue();
        String accessToken = JsonPath.read(registered, "$.accessToken");
        mvc.perform(get("/me").header("Authorization", bearer(accessToken))).andExpect(status().isOk());

        setActive(accessTokenFor(ADMIN_MOBILE, ADMIN_PIN), id, false).andExpect(status().isOk());

        // The access token has not expired, but the account is off.
        mvc.perform(get("/me").header("Authorization", bearer(accessToken))).andExpect(status().isUnauthorized());
    }

    @Test
    void reactivatingClearsALockout() throws Exception {
        String mobile = uniqueMobile();
        String registered = register("Asha", mobile, "4321").andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(registered, "$.user.id")).longValue();
        for (int i = 0; i < 5; i++) {
            login(mobile, "0000");
        }
        login(mobile, "4321").andExpect(status().isTooManyRequests());

        setActive(accessTokenFor(ADMIN_MOBILE, ADMIN_PIN), id, true).andExpect(status().isOk());

        login(mobile, "4321").andExpect(status().isOk());
    }

    @Test
    void adminCannotDeactivateThemselves() throws Exception {
        String body = login(ADMIN_MOBILE, ADMIN_PIN).andReturn().getResponse().getContentAsString();
        long adminId = ((Number) JsonPath.read(body, "$.user.id")).longValue();

        setActive(JsonPath.read(body, "$.accessToken"), adminId, false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_CHANGE_SELF"));
    }

    @Test
    void adminCreatesAnotherAdmin() throws Exception {
        String mobile = uniqueMobile();
        String admin = accessTokenFor(ADMIN_MOBILE, ADMIN_PIN);

        mvc.perform(post("/admin/admins")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Second Teacher", "mobile": "%s", "pin": "246810"}
                                """.formatted(mobile)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        String newAdmin = accessTokenFor(mobile, "246810");
        mvc.perform(get("/admin/users").header("Authorization", bearer(newAdmin)))
                .andExpect(status().isOk());
    }

    @Test
    void resetPinReplacesThePinUnlocksAndEndsOldSessions() throws Exception {
        String mobile = uniqueMobile();
        String registered = register("Asha", mobile, "4321").andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(registered, "$.user.id")).longValue();
        String oldRefresh = JsonPath.read(registered, "$.refreshToken");
        for (int i = 0; i < 5; i++) {
            login(mobile, "0000");
        }
        String admin = accessTokenFor(ADMIN_MOBILE, ADMIN_PIN);
        mvc.perform(get("/admin/users").param("q", mobile).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.content[0].lockedUntil").isNotEmpty());

        resetPin(admin, id, "8642")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mobile").value(mobile))
                .andExpect(jsonPath("$.lockedUntil").value(nullValue()));

        login(mobile, "4321").andExpect(status().isUnauthorized());
        login(mobile, "8642").andExpect(status().isOk());
        postJson("/auth/refresh", "{\"refreshToken\": \"" + oldRefresh + "\"}")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetPinValidatesTheNewPin() throws Exception {
        String registered = register("Asha", uniqueMobile(), "4321").andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(registered, "$.user.id")).longValue();

        resetPin(accessTokenFor(ADMIN_MOBILE, ADMIN_PIN), id, "12")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.pin").value("PIN must be 4 to 6 digits"));
    }

    @Test
    void resetPinIsOnlyForAdminsAndNotForYourself() throws Exception {
        String mobile = uniqueMobile();
        String registered = register("Asha", mobile, "4321").andReturn().getResponse().getContentAsString();
        long studentId = ((Number) JsonPath.read(registered, "$.user.id")).longValue();
        resetPin(JsonPath.read(registered, "$.accessToken"), studentId, "1111")
                .andExpect(status().isForbidden());

        String adminLogin = login(ADMIN_MOBILE, ADMIN_PIN).andReturn().getResponse().getContentAsString();
        long adminId = ((Number) JsonPath.read(adminLogin, "$.user.id")).longValue();
        String admin = JsonPath.read(adminLogin, "$.accessToken");
        resetPin(admin, adminId, "1111")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_CHANGE_SELF"));
        resetPin(admin, 999_999L, "1111")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    private ResultActions resetPin(String adminToken, long userId, String pin) throws Exception {
        return mvc.perform(post("/admin/users/{id}/reset-pin", userId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\": \"" + pin + "\"}"));
    }

    private ResultActions setActive(String adminToken, long userId, boolean active) throws Exception {
        return mvc.perform(patch("/admin/users/{id}", userId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\": " + active + "}"));
    }
}
