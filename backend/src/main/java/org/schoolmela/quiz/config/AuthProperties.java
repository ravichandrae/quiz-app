package org.schoolmela.quiz.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.auth")
public record AuthProperties(
        @NotBlank(message = "set JWT_SECRET (at least 32 characters)")
        @Size(min = 32, message = "JWT_SECRET must be at least 32 characters")
        String jwtSecret,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("7d") Duration refreshTokenTtl,
        @DefaultValue("5") @Min(1) int maxFailedLogins,
        @DefaultValue("15m") Duration lockoutDuration) {
}
