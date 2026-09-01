package za.co.trademesh.shared.events;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wires the event and outbox machinery.
 *
 * <p>{@link EnableScheduling} is required: {@code @Scheduled} methods are found
 * by a post-processor that this annotation registers, and without it the
 * annotations are simply ignored. Nothing fails — the application starts, the
 * outbox fills, and no worker ever runs.
 */
@Configuration
@EnableScheduling
public class EventsConfiguration {

    /**
     * A single injectable clock so that time is a dependency rather than a
     * static call. Tests substitute a fixed clock and assert on exact instants
     * instead of tolerating a window around "now".
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
