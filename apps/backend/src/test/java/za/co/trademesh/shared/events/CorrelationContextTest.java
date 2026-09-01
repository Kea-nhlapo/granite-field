package za.co.trademesh.shared.events;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationContextTest {

    @Test
    void exposesTheScopedCorrelationIdAndActor() {
        UUID correlationId = UUID.randomUUID();

        CorrelationContext.runWithin(correlationId, "user-7", () -> {
            assertThat(CorrelationContext.correlationId()).isEqualTo(correlationId);
            assertThat(CorrelationContext.actor()).contains("user-7");
        });
    }

    /**
     * Request threads are pooled and reused. A scope left open would hand the
     * previous request's actor to whatever request lands on the thread next,
     * which is an authorization-shaped bug rather than a tidiness one.
     */
    @Test
    void clearsTheScopeEvenWhenTheWorkThrows() {
        assertThatThrownBy(() -> CorrelationContext.runWithin(
            UUID.randomUUID(), "user-7", () -> {
                throw new IllegalStateException("handler blew up");
            }))
            .isInstanceOf(IllegalStateException.class);

        assertThat(CorrelationContext.actor()).isEmpty();
    }

    @Test
    void restoresAnOuterScopeWhenAnInnerScopeCloses() {
        UUID outer = UUID.randomUUID();
        UUID inner = UUID.randomUUID();

        CorrelationContext.runWithin(outer, "outer-user", () -> {
            CorrelationContext.runWithin(inner, "inner-user", () ->
                assertThat(CorrelationContext.correlationId()).isEqualTo(inner));

            assertThat(CorrelationContext.correlationId()).isEqualTo(outer);
            assertThat(CorrelationContext.actor()).contains("outer-user");
        });
    }

    @Test
    void fallsBackToAFreshCorrelationIdOutsideAnyScope() {
        assertThat(CorrelationContext.correlationId()).isNotNull();
        assertThat(CorrelationContext.actor()).isEmpty();
    }

    /**
     * Deliberate: the outbox worker restores correlation from the message row
     * it claimed, which is the only correlation true for that work. Inheriting
     * the spawning thread's scope would silently attribute a worker's job to
     * whichever request happened to start the thread.
     */
    @Test
    void doesNotLeakIntoThreadsSpawnedInsideAScope() throws Exception {
        AtomicReference<String> seenInChild = new AtomicReference<>("unset");

        CorrelationContext.runWithin(UUID.randomUUID(), "user-7", () -> {
            Thread child = new Thread(() ->
                seenInChild.set(CorrelationContext.actor().orElse("none")));
            child.start();
            try {
                child.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });

        assertThat(seenInChild.get()).isEqualTo("none");
    }
}
