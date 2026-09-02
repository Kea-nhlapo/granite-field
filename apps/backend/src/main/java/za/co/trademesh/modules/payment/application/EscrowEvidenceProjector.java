package za.co.trademesh.modules.payment.application;

import org.springframework.stereotype.Component;
import za.co.trademesh.modules.evidence.application.EvidenceMetadata;
import za.co.trademesh.modules.evidence.application.EvidenceProjection;
import za.co.trademesh.modules.evidence.application.EvidenceProjector;
import za.co.trademesh.modules.payment.events.PaymentEvent;
import za.co.trademesh.shared.events.DomainEvent;

@Component
class EscrowEvidenceProjector implements EvidenceProjector {

    @Override
    public boolean supports(DomainEvent event) {
        return event instanceof PaymentEvent;
    }

    @Override
    public EvidenceProjection project(DomainEvent event) {
        return switch ((PaymentEvent) event) {
            case PaymentEvent.LockRequested value ->
                projection(
                        value.escrowId(),
                        value.shipmentId(),
                        value.businessId(),
                        "amount",
                        value.amount(),
                        "currency",
                        value.currency());
            case PaymentEvent.LockPending value -> projection(value.escrowId(), value.shipmentId(), value.businessId());
            case PaymentEvent.Locked value ->
                projection(
                        value.escrowId(),
                        value.shipmentId(),
                        value.businessId(),
                        "amount",
                        value.amount(),
                        "currency",
                        value.currency());
            case PaymentEvent.LockFailed value ->
                projection(
                        value.escrowId(), value.shipmentId(), value.businessId(), "failureCode", value.failureCode());
            case PaymentEvent.ReleaseRequested value ->
                projection(
                        value.escrowId(),
                        value.shipmentId(),
                        value.businessId(),
                        "amount",
                        value.amount(),
                        "currency",
                        value.currency());
            case PaymentEvent.ReleasePending value ->
                projection(value.escrowId(), value.shipmentId(), value.businessId());
            case PaymentEvent.Released value ->
                projection(
                        value.escrowId(),
                        value.shipmentId(),
                        value.businessId(),
                        "amount",
                        value.amount(),
                        "currency",
                        value.currency());
            case PaymentEvent.ReleaseFailed value ->
                projection(
                        value.escrowId(), value.shipmentId(), value.businessId(), "failureCode", value.failureCode());
        };
    }

    private static EvidenceProjection projection(
            java.util.UUID escrowId,
            java.util.UUID shipmentId,
            java.util.UUID businessId,
            Object... additionalMetadata) {
        Object[] metadata = new Object[additionalMetadata.length + 4];
        metadata[0] = "businessId";
        metadata[1] = businessId;
        metadata[2] = "shipmentId";
        metadata[3] = shipmentId;
        System.arraycopy(additionalMetadata, 0, metadata, 4, additionalMetadata.length);
        return new EvidenceProjection("ESCROW", escrowId, shipmentId, EvidenceMetadata.of(metadata));
    }
}
