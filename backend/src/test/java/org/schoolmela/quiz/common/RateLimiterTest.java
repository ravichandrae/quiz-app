package org.schoolmela.quiz.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.schoolmela.quiz.SettableClock;

class RateLimiterTest {

    /** 10 seconds into a minute. */
    private final SettableClock clock = new SettableClock(Instant.parse("2026-10-01T10:00:10Z"));

    @Test
    void allowsUpToTheLimitThenSaysHowLongToWait() {
        RateLimiter limiter = new RateLimiter(3, clock);

        assertThat(limiter.acquire("1.2.3.4")).isZero();
        assertThat(limiter.acquire("1.2.3.4")).isZero();
        assertThat(limiter.acquire("1.2.3.4")).isZero();
        // 10 seconds into the minute: 50 seconds until the next one.
        assertThat(limiter.acquire("1.2.3.4")).isEqualTo(50);
        // Other addresses are counted separately.
        assertThat(limiter.acquire("5.6.7.8")).isZero();
    }

    @Test
    void startsAgainInTheNextMinute() {
        RateLimiter limiter = new RateLimiter(1, clock);
        limiter.acquire("1.2.3.4");
        assertThat(limiter.acquire("1.2.3.4")).isPositive();

        clock.advance(Duration.ofSeconds(50));

        assertThat(limiter.acquire("1.2.3.4")).isZero();
    }
}
