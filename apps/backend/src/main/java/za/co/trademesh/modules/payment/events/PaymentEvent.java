package za.co.trademesh.modules.payment.events;

import java.math.BigDecimal;
import java.util.UUID;
import za.co.trademesh.shared.events.DomainEvent;

public sealed interface PaymentEvent extends DomainEvent
        permits PaymentEvent.LockRequested,
                PaymentEvent.LockPending,
                PaymentEvent.Locked,
                PaymentEvent.LockFailed,
                PaymentEvent.ReleaseRequested,
                PaymentEvent.ReleasePending,
                PaymentEvent.Released,
                PaymentEvent.ReleaseFailed {

    @Override
    default int schemaVersion() {
        return 1;
    }

    record LockPending(UUID escrowId, UUID shipmentId, UUID businessId) implements PaymentEvent {
        @Override
        public String type() {
            return "ESCROW_LOCK_PENDING";
        }
    }

    record LockRequested(UUID escrowId, UUID shipmentId, UUID businessId, BigDecimal amount, String currency)
            implements PaymentEvent {
        @Override
        public String type() {
            return "ESCROW_LOCK_REQUESTED";
        }
    }

    record ReleasePending(UUID escrowId, UUID shipmentId, UUID businessId) implements PaymentEvent {
        @Override
        public String type() {
            return "ESCROW_RELEASE_PENDING";
        }
    }

    record Locked(UUID escrowId, UUID shipmentId, UUID businessId, BigDecimal amount, String currency)
            implements PaymentEvent {
        @Override
        public String type() {
            return "ESCROW_LOCKED";
        }
    }

    record LockFailed(UUID escrowId, UUID shipmentId, UUID businessId, String failureCode) implements PaymentEvent {
        @Override
        public String type() {
            return "ESCROW_LOCK_FAILED";
        }
    }

    record ReleaseRequested(UUID escrowId, UUID shipmentId, UUID businessId, BigDecimal amount, String currency)
            implements PaymentEvent {
        @Override
        public String type() {
            return "ESCROW_RELEASE_REQUESTED";
        }
    }

    record Released(
            UUID escrowId, UUID shipmentId, UUID businessId, UUID supplierProfileId, BigDecimal amount, String currency)
            implements PaymentEvent {
        @Override
        public String type() {
            return "ESCROW_RELEASED";
        }

        @Override
        public int schemaVersion() {
            return 2;
        }
    }

    record ReleaseFailed(UUID escrowId, UUID shipmentId, UUID businessId, String failureCode) implements PaymentEvent {
        @Override
        public String type() {
            return "ESCROW_RELEASE_FAILED";
        }
    }
}
