package za.co.trademesh.modules.trust.application;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import za.co.trademesh.modules.business.events.BusinessEvent;
import za.co.trademesh.modules.shipment.application.ShipmentTrustCatalog;
import za.co.trademesh.modules.shipment.events.ShipmentEvent;
import za.co.trademesh.shared.events.PublishedEvent;

@Component
class TrustProjectionListener {

    private final TrustService trust;
    private final ShipmentTrustCatalog shipments;

    TrustProjectionListener(TrustService trust, ShipmentTrustCatalog shipments) {
        this.trust = trust;
        this.shipments = shipments;
    }

    /** Runs after the evidence listener so the snapshot always reads the newly appended fact. */
    @Order(100)
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void rebuild(PublishedEvent<?> published) {
        if (published.event() instanceof BusinessEvent.ProfileConfirmed confirmed) {
            trust.recalculate(confirmed.businessId());
            return;
        }
        if (published.event() instanceof ShipmentEvent.ShipmentStatusChanged changed) {
            shipments.findRequestingBusinessId(changed.shipmentId()).ifPresent(trust::recalculate);
        }
    }
}
