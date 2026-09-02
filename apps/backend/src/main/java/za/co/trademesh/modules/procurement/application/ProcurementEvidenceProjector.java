package za.co.trademesh.modules.procurement.application;

import org.springframework.stereotype.Component;
import za.co.trademesh.modules.evidence.application.EvidenceMetadata;
import za.co.trademesh.modules.evidence.application.EvidenceProjection;
import za.co.trademesh.modules.evidence.application.EvidenceProjector;
import za.co.trademesh.modules.procurement.events.ProcurementEvent;
import za.co.trademesh.shared.events.DomainEvent;

@Component
class ProcurementEvidenceProjector implements EvidenceProjector {

    @Override
    public boolean supports(DomainEvent event) {
        return event instanceof ProcurementEvent;
    }

    @Override
    public EvidenceProjection project(DomainEvent event) {
        ProcurementEvent.OrderConfirmed confirmed = (ProcurementEvent.OrderConfirmed) event;
        return new EvidenceProjection(
                "ORDER",
                confirmed.orderId(),
                null,
                EvidenceMetadata.of(
                        "productRequestId",
                        confirmed.productRequestId(),
                        "buyerBusinessId",
                        confirmed.buyerBusinessId(),
                        "supplierProfileId",
                        confirmed.supplierProfileId(),
                        "currency",
                        confirmed.currency(),
                        "total",
                        confirmed.total()));
    }
}
