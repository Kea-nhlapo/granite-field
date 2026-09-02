package za.co.trademesh.modules.trust.application;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import za.co.trademesh.modules.business.events.BusinessEvent;
import za.co.trademesh.modules.handover.events.HandoverEvent;
import za.co.trademesh.modules.payment.events.PaymentEvent;
import za.co.trademesh.modules.risk.events.RiskEvent;
import za.co.trademesh.modules.shipment.application.ShipmentTrustCatalog;
import za.co.trademesh.modules.shipment.events.ShipmentEvent;
import za.co.trademesh.shared.events.PublishedEvent;

@Component
class TrustProjectionListener {

    private final TrustService trust;
    private final TrustScoreService scores;
    private final ShipmentTrustCatalog shipments;

    TrustProjectionListener(TrustService trust, TrustScoreService scores, ShipmentTrustCatalog shipments) {
        this.trust = trust;
        this.scores = scores;
        this.shipments = shipments;
    }

    /** Runs after the evidence listener so the snapshot always reads the newly appended fact. */
    @Order(100)
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void rebuild(PublishedEvent<?> published) {
        if (published.event() instanceof BusinessEvent.ProfileConfirmed confirmed) {
            trust.recalculate(confirmed.businessId());
            scores.computeProvisional(confirmed.businessId());
            return;
        }
        if (published.event() instanceof ShipmentEvent.ShipmentStatusChanged changed) {
            shipments.findRequestingBusinessId(changed.shipmentId()).ifPresent(trust::recalculate);
            return;
        }
        if (published.event() instanceof HandoverEvent.HandoverFinalized finalized) {
            scores.computeProvisional(finalized.businessId());
            return;
        }
        if (published.event() instanceof PaymentEvent.Locked locked) {
            scores.computeProvisional(locked.businessId());
            return;
        }
        if (published.event() instanceof PaymentEvent.LockFailed failed) {
            scores.computeProvisional(failed.businessId());
            return;
        }
        if (published.event() instanceof PaymentEvent.Released released) {
            scores.computeProvisional(released.businessId());
            return;
        }
        if (published.event() instanceof PaymentEvent.ReleaseFailed failed) {
            scores.computeProvisional(failed.businessId());
            return;
        }
        if (published.event() instanceof RiskEvent.IndicatorOpened opened && opened.affectsTrustScore()) {
            shipments.findRequestingBusinessId(opened.shipmentId()).ifPresent(scores::computeProvisional);
        }
    }
}
