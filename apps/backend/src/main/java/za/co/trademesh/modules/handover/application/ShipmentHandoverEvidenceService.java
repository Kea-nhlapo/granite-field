package za.co.trademesh.modules.handover.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.handover.domain.HandoverRepository;

@Service
class ShipmentHandoverEvidenceService implements ShipmentHandoverEvidenceCatalog {

    private final HandoverRepository handovers;

    ShipmentHandoverEvidenceService(HandoverRepository handovers) {
        this.handovers = handovers;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Handover> find(UUID shipmentId) {
        return handovers.findByShipment(shipmentId).stream()
                .map(challenge -> new Handover(
                        challenge.id(),
                        challenge.type().name(),
                        challenge.deliveryOrderId(),
                        challenge.state().name(),
                        challenge.expectedQuantity(),
                        challenge.unitOfMeasure(),
                        challenge.expectedLocation().label(),
                        challenge.expectedLocation().latitude(),
                        challenge.expectedLocation().longitude(),
                        challenge.locationToleranceMetres(),
                        challenge.expiresAt(),
                        challenge.completedAt(),
                        challenge.createdAt(),
                        challenge.confirmations().stream()
                                .map(confirmation -> new Confirmation(
                                        confirmation.id(),
                                        confirmation.party().name(),
                                        confirmation.observedAt(),
                                        confirmation.receivedAt(),
                                        confirmation.latitude(),
                                        confirmation.longitude(),
                                        confirmation.distanceMetres(),
                                        confirmation.capturedQuantity(),
                                        confirmation.photoUrl(),
                                        confirmation.quantityOutcome().name(),
                                        confirmation.quantityNote()))
                                .toList()))
                .toList();
    }
}
