package za.co.trademesh.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

/** A UTC test clock whose time advances only when the scenario tells it to. */
public final class MutableTestClock extends Clock {

    private Instant current;

    public MutableTestClock(Instant initial) {
        current = Objects.requireNonNull(initial);
    }

    public void set(Instant instant) {
        current = Objects.requireNonNull(instant);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        if (!ZoneOffset.UTC.equals(zone)) {
            throw new IllegalArgumentException("The deterministic test clock is UTC only");
        }
        return this;
    }

    @Override
    public Instant instant() {
        return current;
    }
}
