package org.schoolmela.quiz.common;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts requests per key in fixed one-minute windows, in memory. With several backend replicas
 * each one counts separately, so the effective limit is the limit times the replica count.
 */
public class RateLimiter {

    private static final long WINDOW_MILLIS = Duration.ofMinutes(1).toMillis();
    private static final int MAX_TRACKED_KEYS = 50_000;

    private record Window(long start, int count) {
    }

    private final int limit;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(int limitPerMinute, Clock clock) {
        this.limit = limitPerMinute;
        this.clock = clock;
    }

    /** Counts one request for {@code key}; returns the seconds to wait, or 0 if it is allowed. */
    public long acquire(String key) {
        long now = clock.millis();
        long windowStart = now - now % WINDOW_MILLIS;
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.values().removeIf(w -> w.start() < windowStart);
        }
        Window window = windows.merge(key, new Window(windowStart, 1),
                (old, fresh) -> old.start() == windowStart ? new Window(windowStart, old.count() + 1) : fresh);
        if (window.count() <= limit) {
            return 0;
        }
        return Math.max(1, (windowStart + WINDOW_MILLIS - now + 999) / 1000);
    }
}
