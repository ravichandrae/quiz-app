package org.schoolmela.quiz.auth;

import static org.schoolmela.quiz.user.Credentials.blankToNull;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.schoolmela.quiz.auth.AuthDtos.AuthResponse;
import org.schoolmela.quiz.auth.AuthDtos.LoginRequest;
import org.schoolmela.quiz.auth.AuthDtos.RegisterRequest;
import org.schoolmela.quiz.common.ApiException;
import org.schoolmela.quiz.config.AuthProperties;
import org.schoolmela.quiz.user.Role;
import org.schoolmela.quiz.user.User;
import org.schoolmela.quiz.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final TokenService tokens;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties props;
    private final Clock clock;
    /** Compared against when the mobile number is unknown, so the response time does not reveal it. */
    private final String dummyPinHash;

    public AuthService(UserRepository users, TokenService tokens, PasswordEncoder passwordEncoder,
            AuthProperties props, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
        this.clock = clock;
        this.dummyPinHash = passwordEncoder.encode("000000");
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        User user = createUser(req.name(), req.mobile(), req.pin(), req.email(), req.school(), Role.STUDENT);
        return tokens.issueTokens(user);
    }

    @Transactional
    public User createUser(String name, String mobile, String pin, String email, String school, Role role) {
        if (users.existsByMobile(mobile)) {
            throw mobileTaken();
        }
        User user = new User(name.trim(), mobile, passwordEncoder.encode(pin), blankToNull(email), blankToNull(school),
                role, clock.instant());
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw mobileTaken();
        }
    }

    /**
     * Checks the mobile number and PIN. Wrong PINs are counted even though the request fails,
     * hence {@code noRollbackFor}.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest req) {
        Instant now = clock.instant();
        User user = users.findByMobileForUpdate(req.mobile()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(req.pin(), dummyPinHash);
            throw wrongCredentials();
        }
        if (user.isLockedAt(now)) {
            throw locked(user, now);
        }
        if (!passwordEncoder.matches(req.pin(), user.getPinHash())) {
            user.recordFailedLogin(now, props.maxFailedLogins(), props.lockoutDuration());
            throw user.isLockedAt(now) ? locked(user, now) : wrongCredentials();
        }
        // Checked only after the PIN, so a deactivated account is not revealed to someone guessing.
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED",
                    "Your account is turned off. Please ask your teacher.");
        }
        user.clearFailedLogins();
        return tokens.issueTokens(user);
    }

    private static ApiException wrongCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "Wrong mobile number or PIN. Please try again.");
    }

    private static ApiException locked(User user, Instant now) {
        long seconds = Duration.between(now, user.getLockedUntil()).toSeconds();
        long minutes = Math.max(1, (seconds + 59) / 60);
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "ACCOUNT_LOCKED",
                "Too many wrong tries. Please wait " + minutes + (minutes == 1 ? " minute" : " minutes")
                        + " and try again.");
    }

    private static ApiException mobileTaken() {
        return new ApiException(HttpStatus.CONFLICT, "MOBILE_TAKEN",
                "This mobile number is already registered. Please log in.");
    }
}
