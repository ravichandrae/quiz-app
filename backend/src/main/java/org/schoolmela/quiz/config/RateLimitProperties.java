package org.schoolmela.quiz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param authRequestsPerMinute login, registration and token-refresh requests allowed per client
 *                              network address per minute. A whole class often shares one school
 *                              network address, so this is generous; wrong PINs are limited per
 *                              account separately.
 */
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(@DefaultValue("120") int authRequestsPerMinute) {
}
