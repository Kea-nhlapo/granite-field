package za.co.trademesh.modules.handover.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Completed handover evidence without exposing the QR nonce or internal fingerprints. */
public interface ShipmentHandoverEvidenceCatalog {

    List<Handover> find(UUID shipmentId);

    record Handover(
            UUID challengeId,
            String type,
            UUID deliveryOrderId,
            String state,
            String expectedLocationLabel,
            double expectedLatitude,
            double expectedLongitude,
            int locationToleranceMetres,
            Instant expiresAt,
            Instant completedAt,
            Instant createdAt,
            List<Confirmation> confirmations) {
        public Handover {
            confirmations = List.copyOf(confirmations);
        }
    }

    record Confirmation(
            UUID confirmationId,
            String party,
            Instant observedAt,
            Instant receivedAt,
            double latitude,
            double longitude,
            double distanceMetres,
            String quantityOutcome,
            String quantityNote) {}
}
