package org.schoolmela.quiz.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
    private static final Duration LOCKOUT = Duration.ofMinutes(15);

    private final User user = new User("Asha", "9876543210", "hash", null, null, Role.STUDENT, NOW);

    @Test
    void locksAfterTheMaximumWrongPins() {
        for (int i = 0; i < 4; i++) {
            user.recordFailedLogin(NOW, 5, LOCKOUT);
        }
        assertThat(user.isLockedAt(NOW)).isFalse();

        user.recordFailedLogin(NOW, 5, LOCKOUT);

        assertThat(user.isLockedAt(NOW)).isTrue();
        assertThat(user.isLockedAt(NOW.plus(LOCKOUT).minusSeconds(1))).isTrue();
        assertThat(user.isLockedAt(NOW.plus(LOCKOUT))).isFalse();
    }

    @Test
    void afterALockoutExpiresTheUserGetsAFullSetOfTries() {
        for (int i = 0; i < 5; i++) {
            user.recordFailedLogin(NOW, 5, LOCKOUT);
        }
        Instant later = NOW.plus(LOCKOUT);

        user.recordFailedLogin(later, 5, LOCKOUT);

        assertThat(user.isLockedAt(later)).isFalse();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void clearingFailedLoginsUnlocks() {
        for (int i = 0; i < 5; i++) {
            user.recordFailedLogin(NOW, 5, LOCKOUT);
        }

        user.clearFailedLogins();

        assertThat(user.isLockedAt(NOW)).isFalse();
        assertThat(user.getFailedLoginAttempts()).isZero();
    }
}
