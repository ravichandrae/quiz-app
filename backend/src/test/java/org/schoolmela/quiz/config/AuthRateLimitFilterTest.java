package org.schoolmela.quiz.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.schoolmela.quiz.SettableClock;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthRateLimitFilterTest {

    /** 10 seconds into a minute. */
    private final AuthRateLimitFilter filter = new AuthRateLimitFilter(
            new RateLimitProperties(2), new SettableClock(Instant.parse("2026-10-01T10:00:10Z")));

    private MockHttpServletResponse send(String method, String path, String clientAddress) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.setRemoteAddr(clientAddress);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void limitsLoginsPerClientAddress() throws Exception {
        assertThat(send("POST", "/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);
        assertThat(send("POST", "/auth/register", "10.0.0.1").getStatus()).isEqualTo(200);

        MockHttpServletResponse limited = send("POST", "/auth/refresh", "10.0.0.1");

        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isEqualTo("50");
        assertThat(limited.getContentAsString())
                .contains("\"code\":\"RATE_LIMITED\"")
                .contains("Too many tries from this network. Please wait a minute and try again.");
        assertThat(send("POST", "/auth/login", "10.0.0.2").getStatus()).isEqualTo(200);
    }

    @Test
    void leavesOtherRequestsAlone() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(send("GET", "/me/quizzes", "10.0.0.3").getStatus()).isEqualTo(200);
            assertThat(send("POST", "/auth/logout", "10.0.0.3").getStatus()).isEqualTo(200);
        }
    }
}
