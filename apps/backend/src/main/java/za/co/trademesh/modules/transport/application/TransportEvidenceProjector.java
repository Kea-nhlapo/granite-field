package za.co.trademesh.modules.transport.application;

import org.springframework.stereotype.Component;
import za.co.trademesh.modules.evidence.application.EvidenceMetadata;
import za.co.trademesh.modules.evidence.application.EvidenceProjection;
import za.co.trademesh.modules.evidence.application.EvidenceProjector;
import za.co.trademesh.modules.transport.events.TransportEvent;
import za.co.trademesh.shared.events.DomainEvent;

@Component
class TransportEvidenceProjector implements EvidenceProjector {

    @Override
    public boolean supports(DomainEvent event) {
        return event instanceof TransportEvent;
    }

    @Override
    public EvidenceProjection project(DomainEvent event) {
        return switch ((TransportEvent) event) {
            case TransportEvent.TransporterRegistered registered ->
                new EvidenceProjection(
                        "TRANSPORTER",
                        registered.transporterId(),
                        null,
                        EvidenceMetadata.of("businessId", registered.businessId()));
            case TransportEvent.DriverAssigned assigned ->
                new EvidenceProjection(
                        "DRIVER_ASSIGNMENT",
                        assigned.assignmentId(),
                        null,
                        EvidenceMetadata.of(
                                "transporterId",
                                assigned.transporterId(),
                                "vehicleId",
                                assigned.vehicleId(),
                                "driverId",
                                assigned.driverId()));
            case TransportEvent.CapacityOfferPublished published ->
                new EvidenceProjection(
                        "CAPACITY_OFFER",
                        published.offerId(),
                        null,
                        EvidenceMetadata.of(
                                "transporterId", published.transporterId(), "vehicleId", published.vehicleId()));
            case TransportEvent.CapacityOfferCancelled cancelled ->
                new EvidenceProjection(
                        "CAPACITY_OFFER",
                        cancelled.offerId(),
                        null,
                        EvidenceMetadata.of("transporterId", cancelled.transporterId()));
            case TransportEvent.CapacityMatchCompleted completed ->
                new EvidenceProjection(
                        "CAPACITY_MATCH",
                        completed.matchSearchId(),
                        null,
                        EvidenceMetadata.of(
                                "requestedByBusinessId",
                                completed.requestedByBusinessId(),
                                "demandGroupSuggestionId",
                                completed.demandGroupSuggestionId(),
                                "compatibleOfferCount",
                                completed.compatibleOfferCount()));
            case TransportEvent.CapacityReserved reserved ->
                new EvidenceProjection(
                        "CAPACITY_RESERVATION",
                        reserved.reservationId(),
                        null,
                        EvidenceMetadata.of(
                                "matchSearchId",
                                reserved.matchSearchId(),
                                "offerId",
                                reserved.offerId(),
                                "expiresAt",
                                reserved.expiresAt()));
            case TransportEvent.CapacityReleased released ->
                new EvidenceProjection(
                        "CAPACITY_RESERVATION",
                        released.reservationId(),
                        null,
                        EvidenceMetadata.of(
                                "matchSearchId",
                                released.matchSearchId(),
                                "offerId",
                                released.offerId(),
                                "expired",
                                released.expired()));
        };
    }
}
