package com.wunderlore.game.whosaidit;

import java.time.Clock;
import java.time.Duration;

// A global sliding-window limit: at most `limit` accepted attempts in any `window`. There is no
// per-client state, so memory is one array of `limit` longs, allocated once, whatever the traffic.
//
// The array is a ring of the accept times of the last `limit` accepted attempts. The oldest sits
// at `head`. A new attempt is accepted when there is a free slot or the oldest accept is at least
// one window old, and then it takes the slot. Each call is O(1) under the monitor. A rejected
// attempt records nothing, so it never uses up a slot. The window is half-open: an accept at time
// t stops counting at exactly t + window.
//
// The quota counts attempts to create a round, not open rounds, so answering or expiring a round
// gives nothing back.
public class RoundCreationRateLimiter {

    // Bounds the one allocation (about 80 KB at this size).
    static final int MAX_LIMIT = 10_000;

    public record Decision(boolean allowed, long retryAfterSeconds) {

        static final Decision ALLOWED = new Decision(true, 0);
    }

    private final Clock clock;
    private final long windowMillis;
    private final long[] acceptedAt;

    private int head;
    private int size;
    // Accept times never go backwards, even if the clock does, so the ring stays ordered.
    private long lastMillis = Long.MIN_VALUE;

    public RoundCreationRateLimiter(Clock clock, int limit, Duration window) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException(
                    "Round creation limit must be between 1 and " + MAX_LIMIT);
        }

        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Round creation window must be positive");
        }

        // Millisecond resolution; a window below one millisecond would never expire a slot.
        this.windowMillis = Math.max(1, window.toMillis());
        this.clock = clock;
        this.acceptedAt = new long[limit];
    }

    // Takes a slot if one is available. Otherwise reports how long until one frees up, in whole
    // seconds rounded up, never below 1.
    public synchronized Decision tryAcquire() {
        long now = Math.max(clock.millis(), lastMillis);

        lastMillis = now;

        if (size < acceptedAt.length) {
            acceptedAt[(head + size) % acceptedAt.length] = now;
            size++;

            return Decision.ALLOWED;
        }

        long frees = acceptedAt[head] + windowMillis;

        if (now >= frees) {
            acceptedAt[head] = now;
            head = (head + 1) % acceptedAt.length;

            return Decision.ALLOWED;
        }

        long waitMillis = frees - now;

        return new Decision(false, Math.max(1, (waitMillis + 999) / 1000));
    }
}
