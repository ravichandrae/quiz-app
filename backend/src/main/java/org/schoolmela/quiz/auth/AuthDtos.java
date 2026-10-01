package org.schoolmela.quiz.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.schoolmela.quiz.user.Credentials;
import org.schoolmela.quiz.user.Role;
import org.schoolmela.quiz.user.User;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Please enter your name")
            @Size(max = 100, message = "Name is too long")
            String name,
            @NotNull(message = Credentials.MOBILE_MESSAGE)
            @Pattern(regexp = Credentials.MOBILE_PATTERN, message = Credentials.MOBILE_MESSAGE)
            String mobile,
            @NotNull(message = Credentials.PIN_MESSAGE)
            @Pattern(regexp = Credentials.PIN_PATTERN, message = Credentials.PIN_MESSAGE)
            String pin,
            @Email(message = "Please enter a valid email")
            @Size(max = 254, message = "Email is too long")
            String email,
            @Size(max = 150, message = "School name is too long")
            String school) {
    }

    public record LoginRequest(
            @NotNull(message = Credentials.MOBILE_MESSAGE)
            @Pattern(regexp = Credentials.MOBILE_PATTERN, message = Credentials.MOBILE_MESSAGE)
            String mobile,
            @NotNull(message = Credentials.PIN_MESSAGE)
            @Pattern(regexp = Credentials.PIN_PATTERN, message = Credentials.PIN_MESSAGE)
            String pin) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record AuthResponse(String accessToken, String refreshToken, long expiresIn, UserInfo user) {
    }

    public record UserInfo(Long id, String name, String mobile, Role role) {

        public static UserInfo from(User user) {
            return new UserInfo(user.getId(), user.getName(), user.getMobile(), user.getRole());
        }
    }
}
