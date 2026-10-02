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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Registration and login. Hashing and checking PINs is slow on purpose (BCrypt), so it happens
 * outside database transactions: a whole class logging in at once must not tie up the
 * connection pool for everyone else.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final TokenService tokens;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties props;
    private final Clock clock;
    private final TransactionTemplate tx;
    /** Compared against when the mobile number is unknown, so the response time does not reveal it. */
    private final String dummyPinHash;

    public AuthService(UserRepository users, TokenService tokens, PasswordEncoder passwordEncoder,
            AuthProperties props, Clock clock, TransactionTemplate tx) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
        this.clock = clock;
        this.tx = tx;
        this.dummyPinHash = passwordEncoder.encode("000000");
    }

    public AuthResponse register(RegisterRequest req) {
        String pinHash = passwordEncoder.encode(req.pin());
        return tx.execute(status -> tokens.issueTokens(
                insertUser(req.name(), req.mobile(), pinHash, req.email(), req.school(), Role.STUDENT)));
    }

    public User createUser(String name, String mobile, String pin, String email, String school, Role role) {
        String pinHash = passwordEncoder.encode(pin);
        return tx.execute(status -> insertUser(name, mobile, pinHash, email, school, role));
    }

    private User insertUser(String name, String mobile, String pinHash, String email, String school, Role role) {
        if (users.existsByMobile(mobile)) {
            throw mobileTaken();
        }
        User user = new User(name.trim(), mobile, pinHash, blankToNull(email), blankToNull(school), role,
                clock.instant());
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw mobileTaken();
        }
    }

    /** What a login attempt led to: tokens, or the error to report. */
    private record LoginOutcome(AuthResponse tokens, ApiException failure) {

        static LoginOutcome success(AuthResponse tokens) {
            return new LoginOutcome(tokens, null);
        }

        static LoginOutcome failure(ApiException failure) {
            return new LoginOutcome(null, failure);
        }
    }

    public AuthResponse login(LoginRequest req) {
        Instant now = clock.instant();
        User found = users.findByMobile(req.mobile()).orElse(null);
        if (found == null) {
            passwordEncoder.matches(req.pin(), dummyPinHash);
            throw wrongCredentials();
        }
        if (found.isLockedAt(now)) {
            throw locked(found, now);
        }
        boolean pinMatches = passwordEncoder.matches(req.pin(), found.getPinHash());
        // The outcome is saved first and reported afterwards, so wrong PINs are counted even though
        // the request fails.
        LoginOutcome outcome = tx.execute(status -> recordLogin(req.mobile(), pinMatches, now));
        if (outcome.failure() != null) {
            throw outcome.failure();
        }
        return outcome.tokens();
    }

    /** Counts the attempt with the account locked, so simultaneous wrong PINs are all counted. */
    private LoginOutcome recordLogin(String mobile, boolean pinMatches, Instant now) {
        User user = users.findByMobileForUpdate(mobile).orElseThrow(AuthService::wrongCredentials);
        if (user.isLockedAt(now)) {
            // Another attempt locked the account while this PIN was being checked.
            return LoginOutcome.failure(locked(user, now));
        }
        if (!pinMatches) {
            user.recordFailedLogin(now, props.maxFailedLogins(), props.lockoutDuration());
            return LoginOutcome.failure(user.isLockedAt(now) ? locked(user, now) : wrongCredentials());
        }
        // Checked only after the PIN, so a deactivated account is not revealed to someone guessing.
        if (!user.isActive()) {
            return LoginOutcome.failure(new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED",
                    "Your account is turned off. Please ask your teacher."));
        }
        user.clearFailedLogins();
        return LoginOutcome.success(tokens.issueTokens(user));
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
