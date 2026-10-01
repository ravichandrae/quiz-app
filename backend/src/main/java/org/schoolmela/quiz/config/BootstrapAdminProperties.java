package org.schoolmela.quiz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** The first admin account, created at startup when {@code mobile} and {@code pin} are set. */
@ConfigurationProperties("app.bootstrap-admin")
public record BootstrapAdminProperties(@DefaultValue("Admin") String name, String mobile, String pin) {
}
