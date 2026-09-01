package za.co.trademesh.shared.events;

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
public class EventsConfiguration {}
