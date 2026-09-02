package za.co.trademesh.modules.risk.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import za.co.trademesh.modules.shipment.events.ShipmentEvent;
import za.co.trademesh.modules.telemetry.events.TelemetryEvent;
import za.co.trademesh.shared.events.PublishedEvent;

@Component
class RiskEventHandlers {

    private final RiskService risk;

    RiskEventHandlers(RiskService risk) {
        this.risk = risk;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTelemetry(PublishedEvent<TelemetryEvent.ReadingAccepted> published) {
        risk.evaluateTelemetry(published.event().readingId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAssignmentChanged(PublishedEvent<ShipmentEvent.ShipmentAssignmentChanged> published) {
        var event = published.event();
        risk.evaluateDriverChange(
                event.shipmentId(),
                event.previousAssignmentId(),
                event.assignmentId(),
                event.driverId(),
                published.envelope().occurredAt());
    }
}
