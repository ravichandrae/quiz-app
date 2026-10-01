package org.schoolmela.quiz.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.schoolmela.quiz.IntegrationTest;
import org.schoolmela.quiz.user.User;
import org.schoolmela.quiz.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;

class AuthIntegrationTest extends IntegrationTest {

    @Autowired
    UserRepository users;

    @Test
    void registerLogsTheStudentInAndStoresAHashedPin() throws Exception {
        String mobile = uniqueMobile();

        String body = postJson("/auth/register", """
                {"name": "  Asha  ", "mobile": "%s", "pin": "4321", "email": "", "school": "ZP High School"}
                """.formatted(mobile))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.name").value("Asha"))
                .andExpect(jsonPath("$.user.role").value("STUDENT"))
                .andReturn().getResponse().getContentAsString();

        User saved = users.findByMobile(mobile).orElseThrow();
        assertThat(saved.getPinHash()).doesNotContain("4321").startsWith("$2");
        assertThat(saved.getEmail()).isNull();
        assertThat(saved.getSchool()).isEqualTo("ZP High School");

        mvc.perform(get("/me").header("Authorization", bearer(JsonPath.read(body, "$.accessToken"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mobile").value(mobile));
    }

    @Test
    void registerRejectsADuplicateMobileNumber() throws Exception {
        String mobile = uniqueMobile();
        register("Asha", mobile, "4321").andExpect(status().isCreated());

        register("Ravi", mobile, "1111")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MOBILE_TAKEN"));
    }

    @Test
    void registerExplainsEachInvalidField() throws Exception {
        postJson("/auth/register", """
                {"name": " ", "mobile": "12345", "pin": "12", "email": "not-an-email"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.name").value("Please enter your name"))
                .andExpect(jsonPath("$.errors.mobile").value("Mobile number must be 10 digits"))
                .andExpect(jsonPath("$.errors.pin").value("PIN must be 4 to 6 digits"))
                .andExpect(jsonPath("$.errors.email").value("Please enter a valid email"));
    }

    @Test
    void wrongPinAndUnknownMobileGiveTheSameError() throws Exception {
        String mobile = uniqueMobile();
        register("Asha", mobile, "4321").andExpect(status().isCreated());

        login(mobile, "0000")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Wrong mobile number or PIN. Please try again."));
        login(uniqueMobile(), "4321")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void fiveWrongPinsLockTheAccount() throws Exception {
        String mobile = uniqueMobile();
        register("Asha", mobile, "4321").andExpect(status().isCreated());

        for (int i = 0; i < 4; i++) {
            login(mobile, "0000").andExpect(status().isUnauthorized());
        }
        login(mobile, "0000")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.detail").value("Too many wrong tries. Please wait 15 minutes and try again."));

        // Even the right PIN is refused while locked.
        login(mobile, "4321").andExpect(status().isTooManyRequests());
    }

    @Test
    void aCorrectPinResetsTheWrongPinCount() throws Exception {
        String mobile = uniqueMobile();
        register("Asha", mobile, "4321").andExpect(status().isCreated());

        for (int i = 0; i < 4; i++) {
            login(mobile, "0000").andExpect(status().isUnauthorized());
        }
        login(mobile, "4321").andExpect(status().isOk());
        login(mobile, "0000").andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTheRefreshToken() throws Exception {
        String mobile = uniqueMobile();
        String first = register("Asha", mobile, "4321").andReturn().getResponse().getContentAsString();
        String oldRefresh = JsonPath.read(first, "$.refreshToken");

        String second = postJson("/auth/refresh", refreshBody(oldRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mobile").value(mobile))
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(second, "$.refreshToken")).isNotEqualTo(oldRefresh);

        postJson("/auth/refresh", refreshBody(oldRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        String body = register("Asha", uniqueMobile(), "4321").andReturn().getResponse().getContentAsString();
        String refresh = JsonPath.read(body, "$.refreshToken");

        postJson("/auth/logout", refreshBody(refresh)).andExpect(status().isNoContent());

        postJson("/auth/refresh", refreshBody(refresh)).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTamperedAccessTokens() throws Exception {
        String body = register("Asha", uniqueMobile(), "4321").andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        mvc.perform(get("/me").header("Authorization", bearer(tampered)))
                .andExpect(status().isUnauthorized());
    }

    private static String refreshBody(String refreshToken) {
        return """
                {"refreshToken": "%s"}
                """.formatted(refreshToken);
    }
}
