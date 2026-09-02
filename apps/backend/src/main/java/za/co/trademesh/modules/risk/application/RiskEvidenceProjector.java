package za.co.trademesh.modules.risk.application;

import org.springframework.stereotype.Component;
import za.co.trademesh.modules.evidence.application.EvidenceMetadata;
import za.co.trademesh.modules.evidence.application.EvidenceProjection;
import za.co.trademesh.modules.evidence.application.EvidenceProjector;
import za.co.trademesh.modules.risk.events.RiskEvent;
import za.co.trademesh.shared.events.DomainEvent;

@Component
class RiskEvidenceProjector implements EvidenceProjector {

    @Override
    public boolean supports(DomainEvent event) {
        return event instanceof RiskEvent;
    }

    @Override
    public EvidenceProjection project(DomainEvent event) {
        return switch ((RiskEvent) event) {
            case RiskEvent.IndicatorOpened opened ->
                new EvidenceProjection(
                        "RISK_INDICATOR",
                        opened.indicatorId(),
                        opened.shipmentId(),
                        EvidenceMetadata.of("rule", opened.rule(), "severity", opened.severity()));
            case RiskEvent.IndicatorStateChanged changed ->
                new EvidenceProjection(
                        "RISK_INDICATOR",
                        changed.indicatorId(),
                        changed.shipmentId(),
                        EvidenceMetadata.of("fromState", changed.fromState(), "toState", changed.toState()));
        };
    }
}
