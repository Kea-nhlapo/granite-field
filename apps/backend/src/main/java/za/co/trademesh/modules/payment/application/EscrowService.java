package za.co.trademesh.modules.payment.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.delivery.events.DeliveryEvent;
import za.co.trademesh.modules.handover.application.DeliveryReleaseGate;
import za.co.trademesh.modules.payment.domain.Escrow;
import za.co.trademesh.modules.payment.domain.EscrowRepository;
import za.co.trademesh.modules.payment.domain.EscrowStatus;
import za.co.trademesh.modules.payment.domain.EscrowTransaction;
import za.co.trademesh.modules.payment.domain.EscrowTransactionStatus;
import za.co.trademesh.modules.payment.domain.EscrowTransactionType;
import za.co.trademesh.modules.payment.events.PaymentEvent;
import za.co.trademesh.modules.shipment.application.ShipmentEscrowCatalog;
import za.co.trademesh.shared.events.DomainEvents;
import za.co.trademesh.shared.security.SensitiveDataProtector;

@Service
public class EscrowService {

    private final EscrowRepository escrows;
    private final EscrowContextResolver contexts;
    private final ShipmentEscrowCatalog shipments;
    private final DeliveryReleaseGate deliveryReleaseGate;
    private final SensitiveDataProtector dataProtector;
    private final EscrowOutboxRequests requests;
    private final EscrowProperties properties;
    private final DomainEvents events;
    private final Clock clock;

    public EscrowService(
            EscrowRepository escrows,
            EscrowContextResolver contexts,
            ShipmentEscrowCatalog shipments,
            DeliveryReleaseGate deliveryReleaseGate,
            SensitiveDataProtector dataProtector,
            EscrowOutboxRequests requests,
            EscrowProperties properties,
            DomainEvents events,
            Clock clock) {
        this.escrows = escrows;
        this.contexts = contexts;
        this.shipments = shipments;
        this.deliveryReleaseGate = deliveryReleaseGate;
        this.dataProtector = dataProtector;
        this.requests = requests;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public EscrowSnapshot prepareLock(DeliveryEvent.DeliveryAccepted accepted) {
        Optional<Escrow> existing = escrows.find(accepted.businessId(), accepted.shipmentId());
        if (existing.isPresent()) {
            return snapshot(existing.get());
        }
        var context = contexts.resolve(accepted.proposalId(), accepted.shipmentId(), accepted.businessId());
        Instant now = now();
        Escrow escrow = new Escrow(
                UUID.randomUUID(),
                context.shipmentId(),
                context.businessId(),
                context.supplierProfileId(),
                dataProtector.protect(context.supplierPhone()),
                context.currency(),
                context.amount(),
                EscrowStatus.LOCK_REQUESTED,
                now,
                now);
        EscrowTransaction transaction = transaction(
                escrow.id(),
                accepted.proposalId(),
                EscrowTransactionType.LOCK,
                0,
                dataProtector.protect(context.payerPhone()),
                escrow.agreedAmount(),
                now);
        if (!escrows.saveInitial(escrow, transaction)) {
            return escrows.find(accepted.businessId(), accepted.shipmentId())
                    .map(this::snapshot)
                    .orElseThrow(EscrowException::commandConflict);
        }
        requests.submit(transaction.id());
        events.publish(new PaymentEvent.LockRequested(
                escrow.id(), escrow.shipmentId(), escrow.businessId(), escrow.agreedAmount(), escrow.currency()));
        return snapshot(escrow);
    }

    @Transactional(readOnly = true)
    public EscrowSnapshot get(UUID businessId, UUID shipmentId) {
        return escrows.find(requiredId(businessId), requiredId(shipmentId))
                .map(this::snapshot)
                .orElseThrow(EscrowException::notFound);
    }

    @Transactional
    public EscrowSnapshot retryLock(UUID businessId, UUID shipmentId, UUID requestId) {
        UUID commandId = requiredId(requestId);
        Escrow escrow = lockedEscrowForUpdate(businessId, shipmentId);
        Optional<EscrowTransaction> existing =
                escrows.findTransactionByCommand(escrow.id(), EscrowTransactionType.LOCK, commandId);
        if (existing.isPresent()) {
            return snapshot(escrow);
        }
        if (escrow.status() != EscrowStatus.LOCK_FAILED) {
            throw EscrowException.invalidState();
        }
        EscrowTransaction previous = escrows.findLatestTransaction(escrow.id(), EscrowTransactionType.LOCK)
                .orElseThrow(EscrowException::invalidState);
        Instant now = now();
        EscrowTransaction retry = transaction(
                escrow.id(),
                commandId,
                EscrowTransactionType.LOCK,
                escrows.nextSequence(escrow.id(), EscrowTransactionType.LOCK),
                previous.protectedPhone(),
                escrow.agreedAmount(),
                now);
        addTransactionAndMove(escrow, EscrowStatus.LOCK_REQUESTED, retry, now);
        requests.submit(retry.id());
        events.publish(new PaymentEvent.LockRequested(
                escrow.id(), escrow.shipmentId(), escrow.businessId(), escrow.agreedAmount(), escrow.currency()));
        return snapshot(escrow.id());
    }

    @Transactional
    public EscrowSnapshot release(UUID businessId, UUID shipmentId, UUID requestId, BigDecimal resolvedAmount) {
        UUID commandId = requiredId(requestId);
        BigDecimal amount = positiveAmount(resolvedAmount);
        Escrow escrow = lockedEscrowForUpdate(businessId, shipmentId);
        Optional<EscrowTransaction> existing =
                escrows.findTransactionByCommand(escrow.id(), EscrowTransactionType.RELEASE, commandId);
        if (existing.isPresent()) {
            if (existing.get().amount().compareTo(amount) != 0) {
                throw EscrowException.commandConflict();
            }
            return snapshot(escrow);
        }
        if (amount.compareTo(escrow.agreedAmount()) > 0) {
            throw EscrowException.invalidCommand();
        }
        var shipment =
                shipments.find(escrow.businessId(), escrow.shipmentId()).orElseThrow(EscrowException::releaseBlocked);
        if (!shipment.releaseAllowed()
                && !deliveryReleaseGate.releaseAllowed(escrow.businessId(), escrow.shipmentId(), shipment.orderIds())) {
            throw EscrowException.releaseBlocked();
        }
        if (escrow.status() != EscrowStatus.LOCKED && escrow.status() != EscrowStatus.RELEASE_FAILED) {
            throw EscrowException.invalidState();
        }
        Instant now = now();
        EscrowTransaction release = transaction(
                escrow.id(),
                commandId,
                EscrowTransactionType.RELEASE,
                escrows.nextSequence(escrow.id(), EscrowTransactionType.RELEASE),
                escrow.protectedSupplierPhone(),
                amount,
                now);
        addTransactionAndMove(escrow, EscrowStatus.RELEASE_REQUESTED, release, now);
        requests.submit(release.id());
        events.publish(new PaymentEvent.ReleaseRequested(
                escrow.id(), escrow.shipmentId(), escrow.businessId(), amount, escrow.currency()));
        return snapshot(escrow.id());
    }

    @Transactional
    public EscrowSnapshot resolveAndRelease(
            UUID businessId, UUID shipmentId, UUID requestId, BigDecimal resolvedAmount, UUID actorUserId) {
        deliveryReleaseGate.resolve(
                requiredId(businessId),
                requiredId(shipmentId),
                requiredId(requestId),
                positiveAmount(resolvedAmount),
                requiredId(actorUserId));
        return release(businessId, shipmentId, requestId, resolvedAmount);
    }

    @Transactional(readOnly = true)
    public Optional<ProviderInstruction> providerInstruction(UUID transactionId) {
        return escrows.findTransaction(transactionId)
                .map(transaction -> new ProviderInstruction(
                        transaction.id(),
                        transaction.providerReference(),
                        transaction.type(),
                        dataProtector.unprotect(transaction.protectedPhone()),
                        transaction.amount(),
                        transaction.status(),
                        transaction.deadlineAt()));
    }

    @Transactional
    public void markPending(UUID transactionId) {
        EscrowTransaction transaction = escrows.findTransaction(transactionId).orElseThrow(EscrowException::notFound);
        if (transaction.status() == EscrowTransactionStatus.PENDING
                || transaction.status().finalState()) {
            return;
        }
        Escrow escrow = escrows.findById(transaction.escrowId()).orElseThrow(EscrowException::notFound);
        Instant now = now();
        if (!escrows.updateTransactionStatus(
                transaction.id(), EscrowTransactionStatus.REQUESTED, EscrowTransactionStatus.PENDING, null, now)) {
            return;
        }
        EscrowStatus pending = transaction.type() == EscrowTransactionType.LOCK
                ? EscrowStatus.LOCK_PENDING
                : EscrowStatus.RELEASE_PENDING;
        if (!escrows.updateEscrowStatus(escrow.id(), escrow.status(), pending, now)) {
            throw EscrowException.commandConflict();
        }
        if (transaction.type() == EscrowTransactionType.LOCK) {
            events.publish(new PaymentEvent.LockPending(escrow.id(), escrow.shipmentId(), escrow.businessId()));
        } else {
            events.publish(new PaymentEvent.ReleasePending(escrow.id(), escrow.shipmentId(), escrow.businessId()));
        }
    }

    @Transactional
    public void complete(UUID transactionId, MomoClient.TransactionStatus providerStatus) {
        if (providerStatus == MomoClient.TransactionStatus.PENDING
                || providerStatus == MomoClient.TransactionStatus.UNKNOWN) {
            return;
        }
        EscrowTransaction transaction = escrows.findTransaction(transactionId).orElseThrow(EscrowException::notFound);
        if (transaction.status().finalState()) {
            return;
        }
        if (providerStatus == MomoClient.TransactionStatus.SUCCESSFUL) {
            finish(transaction, EscrowTransactionStatus.SUCCESSFUL, null);
        } else {
            finish(transaction, EscrowTransactionStatus.FAILED, "MOMO_TRANSACTION_FAILED");
        }
    }

    @Transactional
    public void fail(UUID transactionId, String failureCode) {
        EscrowTransaction transaction = escrows.findTransaction(transactionId).orElseThrow(EscrowException::notFound);
        if (!transaction.status().finalState()) {
            finish(transaction, EscrowTransactionStatus.FAILED, safeFailureCode(failureCode));
        }
    }

    @Transactional
    public void timeOut(UUID transactionId) {
        EscrowTransaction transaction = escrows.findTransaction(transactionId).orElseThrow(EscrowException::notFound);
        if (!transaction.status().finalState()) {
            finish(transaction, EscrowTransactionStatus.TIMED_OUT, "MOMO_TRANSACTION_TIMEOUT");
        }
    }

    private void finish(EscrowTransaction transaction, EscrowTransactionStatus transactionStatus, String failureCode) {
        Escrow escrow = escrows.findById(transaction.escrowId()).orElseThrow(EscrowException::notFound);
        Instant now = now();
        if (!escrows.updateTransactionStatus(
                transaction.id(), transaction.status(), transactionStatus, failureCode, now)) {
            return;
        }
        boolean success = transactionStatus == EscrowTransactionStatus.SUCCESSFUL;
        EscrowStatus target =
                switch (transaction.type()) {
                    case LOCK -> success ? EscrowStatus.LOCKED : EscrowStatus.LOCK_FAILED;
                    case RELEASE -> success ? EscrowStatus.RELEASED : EscrowStatus.RELEASE_FAILED;
                };
        if (!escrows.updateEscrowStatus(escrow.id(), escrow.status(), target, now)) {
            throw EscrowException.commandConflict();
        }
        if (transaction.type() == EscrowTransactionType.LOCK) {
            if (success) {
                events.publish(new PaymentEvent.Locked(
                        escrow.id(),
                        escrow.shipmentId(),
                        escrow.businessId(),
                        transaction.amount(),
                        escrow.currency()));
            } else {
                events.publish(new PaymentEvent.LockFailed(
                        escrow.id(), escrow.shipmentId(), escrow.businessId(), failureCode));
            }
        } else if (success) {
            events.publish(new PaymentEvent.Released(
                    escrow.id(), escrow.shipmentId(), escrow.businessId(), transaction.amount(), escrow.currency()));
        } else {
            events.publish(
                    new PaymentEvent.ReleaseFailed(escrow.id(), escrow.shipmentId(), escrow.businessId(), failureCode));
        }
    }

    private void addTransactionAndMove(Escrow escrow, EscrowStatus target, EscrowTransaction transaction, Instant now) {
        if (!escrows.addTransaction(transaction)) {
            throw EscrowException.commandConflict();
        }
        if (!escrows.updateEscrowStatus(escrow.id(), escrow.status(), target, now)) {
            throw EscrowException.commandConflict();
        }
    }

    private Escrow lockedEscrowForUpdate(UUID businessId, UUID shipmentId) {
        return escrows.findForUpdate(requiredId(businessId), requiredId(shipmentId))
                .orElseThrow(EscrowException::notFound);
    }

    private EscrowSnapshot snapshot(UUID escrowId) {
        return escrows.findById(escrowId).map(this::snapshot).orElseThrow(EscrowException::notFound);
    }

    private EscrowSnapshot snapshot(Escrow escrow) {
        return EscrowSnapshot.from(escrow, escrows.findTransactions(escrow.id()));
    }

    private EscrowTransaction transaction(
            UUID escrowId,
            UUID commandId,
            EscrowTransactionType type,
            int sequence,
            String protectedPhone,
            BigDecimal amount,
            Instant now) {
        return new EscrowTransaction(
                UUID.randomUUID(),
                escrowId,
                commandId,
                type,
                sequence,
                UUID.randomUUID(),
                protectedPhone,
                amount,
                EscrowTransactionStatus.REQUESTED,
                now.plus(properties.transactionTimeout()),
                null,
                now,
                now);
    }

    private static UUID requiredId(UUID value) {
        if (value == null) {
            throw EscrowException.invalidCommand();
        }
        return value;
    }

    private static BigDecimal positiveAmount(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.scale() > 4) {
            throw EscrowException.invalidCommand();
        }
        return value;
    }

    private static String safeFailureCode(String value) {
        if (value == null || value.isBlank()) {
            return "MOMO_TRANSACTION_FAILED";
        }
        return value.length() > 128 ? value.substring(0, 128) : value;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    public record ProviderInstruction(
            UUID transactionId,
            UUID providerReference,
            EscrowTransactionType type,
            String phoneNumber,
            BigDecimal amount,
            EscrowTransactionStatus status,
            Instant deadlineAt) {

        MomoClient.Product product() {
            return type == EscrowTransactionType.LOCK
                    ? MomoClient.Product.COLLECTIONS
                    : MomoClient.Product.DISBURSEMENTS;
        }
    }
}
