package za.co.trademesh.shared.events.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param batchSize         messages claimed per poll
 * @param maxAttempts       attempts before a message is marked DEAD
 * @param baseBackoff       first retry delay; doubles per attempt
 * @param maxBackoff        ceiling for the doubling, so attempt 20 does not
 *                          schedule a retry a fortnight out
 * @param visibilityTimeout how long a claim is honoured before the reaper
 *                          assumes the worker died and returns the message to
 *                          PENDING. Must exceed the slowest handler, or a slow
 *                          handler gets a second worker running beside it.
 * @param enabled           lets a deployment run the application without a
 *                          worker — useful for a web-only instance
 */
@ConfigurationProperties("trademesh.outbox")
public record OutboxProperties(
        int batchSize,
        int maxAttempts,
        Duration baseBackoff,
        Duration maxBackoff,
        Duration visibilityTimeout,
        Boolean enabled) {

    public OutboxProperties {
        if (batchSize <= 0) {
            batchSize = 50;
        }
        if (maxAttempts <= 0) {
            maxAttempts = 8;
        }
        if (baseBackoff == null || baseBackoff.isNegative() || baseBackoff.isZero()) {
            baseBackoff = Duration.ofSeconds(2);
        }
        if (maxBackoff == null || maxBackoff.compareTo(baseBackoff) < 0) {
            maxBackoff = Duration.ofHours(1);
        }
        if (visibilityTimeout == null || visibilityTimeout.isNegative() || visibilityTimeout.isZero()) {
            visibilityTimeout = Duration.ofMinutes(5);
        }
        // Boxed deliberately. A primitive boolean binds an absent property to
        // false, which would ship a deployment whose worker never runs and
        // whose queue silently grows.
        if (enabled == null) {
            enabled = Boolean.TRUE;
        }
    }
}
