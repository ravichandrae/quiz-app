package org.schoolmela.quiz;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Base for tests that run the whole app against a real Postgres (one shared context and container). */
@SpringBootTest(properties = {
        "app.auth.jwt-secret=test-secret-test-secret-test-secret-0123",
        "app.bootstrap-admin.name=Head Teacher",
        "app.bootstrap-admin.mobile=" + IntegrationTest.ADMIN_MOBILE,
        "app.bootstrap-admin.pin=" + IntegrationTest.ADMIN_PIN,
})
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public abstract class IntegrationTest {

    public static final String ADMIN_MOBILE = "9000000000";
    public static final String ADMIN_PIN = "123456";

    private static final AtomicLong NEXT_MOBILE = new AtomicLong(8_000_000_000L);

    @Autowired
    protected MockMvc mvc;

    /** A mobile number no other test has used, since tests share one database. */
    protected static String uniqueMobile() {
        return Long.toString(NEXT_MOBILE.getAndIncrement());
    }

    protected ResultActions postJson(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions register(String name, String mobile, String pin) throws Exception {
        return postJson("/auth/register", """
                {"name": "%s", "mobile": "%s", "pin": "%s"}
                """.formatted(name, mobile, pin));
    }

    protected ResultActions login(String mobile, String pin) throws Exception {
        return postJson("/auth/login", """
                {"mobile": "%s", "pin": "%s"}
                """.formatted(mobile, pin));
    }

    protected String accessTokenFor(String mobile, String pin) throws Exception {
        String body = login(mobile, pin).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
