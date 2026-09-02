package za.co.trademesh.shared.events.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import za.co.trademesh.shared.events.CorrelationContext;

/**
 * Claims due outbox messages and runs their handlers.
 *
 * <p>Each poll is three steps, deliberately separated:
 *
 * <ol>
 *   <li>one short transaction claims a batch and commits;
 *   <li>handlers run with NO transaction held by this class;
 *   <li>one short transaction per message records the outcome.
 * </ol>
 *
 * <p>Step 2 is the reason for the split. A handler may make a slow external
 * call, and holding the claiming transaction open across it would pin a pooled
 * connection idle-in-transaction for that whole duration — a handful of slow
 * messages exhausts the pool and takes the web tier down with it. Committing
 * the claim first costs a second round trip and buys back the connection.
 *
 * <p>Transactions are opened with an explicit {@link TransactionTemplate}
 * rather than {@code @Transactional}. These boundaries are crossed by calls
 * from inside this same class, and a self-invocation never passes through the
 * Spring proxy — the annotation would be silently inert and every step would
 * run in whatever transaction, or none, it inherited.
 */
@Component
public class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

    private final OutboxRepository repository;
    private final Map<String, OutboxHandler> handlers;
    private final OutboxProperties properties;
    private final Clock clock;
    private final ObjectProvider<PlatformTransactionManager> transactionManagerProvider;

    private volatile TransactionTemplate transactions;

    /**
     * The transaction manager is resolved on first use, for the same reason
     * {@link OutboxRepository} defers its {@code JdbcClient}: it comes from
     * datasource autoconfiguration, and a context deliberately started without
     * a datasource would otherwise fail at startup on a bean it never uses.
     */
    public OutboxWorker(
        OutboxRepository repository,
        List<OutboxHandler> handlers,
        OutboxProperties properties,
        Clock clock,
        ObjectProvider<PlatformTransactionManager> transactionManagerProvider) {

        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
        this.transactionManagerProvider = transactionManagerProvider;

        this.handlers = handlers.stream().collect(
            Collectors.toUnmodifiableMap(OutboxHandler::type, Function.identity()));
    }

    private TransactionTemplate transactions() {
        TransactionTemplate existing = transactions;
        if (existing != null) {
            return existing;
        }
        // A benign race only builds an equivalent template twice; both are
        // stateless wrappers over the same manager, so no lock is warranted.
        TransactionTemplate created = new TransactionTemplate(transactionManagerProvider.getObject());
        created.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactions = created;
        return created;
    }

    /**
     * Runs one poll cycle and returns how many messages were dispatched.
     *
     * <p>Returning the count lets a test drive the worker directly rather than
     * sleeping until the scheduler happens to fire, which is the difference
     * between a deterministic test and a flaky one.
     */
    public int pollOnce() {
        Instant now = Instant.now(clock);
        List<OutboxMessage> claimed = transactions().execute(status ->
            repository.claimBatch(properties.batchSize(), now));

        if (claimed == null || claimed.isEmpty()) {
            return 0;
        }
        for (OutboxMessage message : claimed) {
            dispatch(message);
        }
        return claimed.size();
    }

    /** Returns expired claims to PENDING, and reports how many were recovered. */
    public int reapOnce() {
        Integer reaped = transactions().execute(status ->
            repository.reapExpiredClaims(properties.visibilityTimeout()));

        int count = reaped == null ? 0 : reaped;
        if (count > 0) {
            log.warn("Returned {} outbox messages to PENDING after their claim expired; "
                + "a worker very likely died mid-dispatch", count);
        }
        return count;
    }

    private void dispatch(OutboxMessage message) {
        OutboxHandler handler = handlers.get(message.type());

        if (handler == null) {
            // Not retryable: no amount of waiting registers a missing bean.
            // Retrying would burn every attempt and reach the same end later,
            // so fail it straight to DEAD where an operator can see it.
            recordDead(message, "no handler registered for type " + message.type());
            return;
        }

        // The work belongs to whoever caused the message, not to this thread.
        // Restored from the row because the publishing request's thread-local
        // state is long gone by the time the worker runs.
        //
        // callWithin returns the handler's failure instead of throwing it, so
        // there is nothing to wrap into an unchecked type and unwrap again on
        // the way out — handler.handle's checked Exception never has to cross
        // the lambda boundary in the first place.
        Exception failure = CorrelationContext.callWithin(
            message.correlationId(),
            message.actor().orElse(null),
            () -> attempt(handler, message));

        if (failure == null) {
            recordDone(message);
        } else {
            recordFailure(message, failure);
        }
    }

    private Exception attempt(OutboxHandler handler, OutboxMessage message) {
        try {
            handler.handle(message);
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    private void recordDone(OutboxMessage message) {
        warnIfClaimAlreadyRevoked(message,
            transactions().execute(status -> repository.markDone(message.id(), message.claimedAt())));
    }

    private void recordFailure(OutboxMessage message, Exception failure) {
        String error = failure.getClass().getName() + ": " + failure.getMessage();

        if (message.attempts() >= properties.maxAttempts()) {
            log.error("Outbox message {} ({}) failed on attempt {} of {} and is now DEAD",
                message.id(), message.type(), message.attempts(), properties.maxAttempts(), failure);
            recordDead(message, error);
            return;
        }

        Instant retryAt = Instant.now(clock).plus(backoffFor(message.attempts()));
        log.warn("Outbox message {} ({}) failed on attempt {}; retrying at {}",
            message.id(), message.type(), message.attempts(), retryAt, failure);

        warnIfClaimAlreadyRevoked(message, transactions().execute(status ->
            repository.markForRetry(message.id(), message.claimedAt(), retryAt, error)));
    }

    private void recordDead(OutboxMessage message, String error) {
        warnIfClaimAlreadyRevoked(message, transactions().execute(status ->
            repository.markDead(message.id(), message.claimedAt(), error)));
    }

    /**
     * Every outcome update is guarded by the claim token, so every one of
     * them can lose the same race: the reaper revokes this worker's claim
     * while the handler is still running, another worker re-claims the
     * message, and this worker's eventual outcome update matches no row.
     *
     * <p>The three callers used to handle this individually; only the success
     * path logged it, so a DEAD or retried message could lose this race with
     * nothing recorded anywhere — a silent gap review found even the earlier
     * ownership-token fix (which stops the corruption) had left in place.
     */
    private void warnIfClaimAlreadyRevoked(OutboxMessage message, Boolean updated) {
        if (!Boolean.TRUE.equals(updated)) {
            log.warn("Outbox message {} ({}) was already re-claimed by another worker "
                + "when this worker tried to record its outcome; the handler outran the "
                + "visibility timeout and the work may run twice",
                message.id(), message.type());
        }
    }

    /**
     * Exponential backoff with jitter, capped.
     *
     * <p>The jitter is not decoration. When a downstream dependency goes down,
     * every in-flight message fails in the same poll; without jitter every
     * retry is scheduled for the same instant and they come back as a
     * synchronised burst, repeatedly, for as long as the outage lasts.
     *
     * <p>The doubling is done on a long and clamped before conversion, so a
     * large attempt count cannot overflow into a negative delay.
     */
    Duration backoffFor(int attempts) {
        long baseSeconds = Math.max(properties.baseBackoff().toSeconds(), 1);
        long capSeconds = Math.max(properties.maxBackoff().toSeconds(), baseSeconds);

        int exponent = Math.max(Math.min(attempts - 1, 32), 0);
        long doubled = baseSeconds << exponent;
        long capped = Math.min(doubled <= 0 ? capSeconds : doubled, capSeconds);

        long half = capped / 2;
        long jittered = half + ThreadLocalRandom.current().nextLong(half + 1);
        return Duration.ofSeconds(Math.max(jittered, 1));
    }
}
