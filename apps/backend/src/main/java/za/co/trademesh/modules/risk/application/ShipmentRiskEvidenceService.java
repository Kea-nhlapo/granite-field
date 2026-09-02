package za.co.trademesh.modules.risk.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.risk.domain.RiskRepository;

@Service
class ShipmentRiskEvidenceService implements ShipmentRiskEvidenceCatalog {

    private final RiskRepository risks;

    ShipmentRiskEvidenceService(RiskRepository risks) {
        this.risks = risks;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RiskIndicator> find(UUID shipmentId) {
        return risks.findByShipment(shipmentId).stream()
                .map(indicator -> new RiskIndicator(
                        indicator.id(),
                        indicator.rule().name(),
                        indicator.ruleVersion(),
                        indicator.severity().name(),
                        indicator.explanation(),
                        indicator.state().name(),
                        indicator.firstObservedAt(),
                        indicator.lastObservedAt(),
                        indicator.evidence().stream()
                                .map(reference -> new EvidenceReference(
                                        reference.evidenceType(), reference.evidenceId(), reference.observedAt()))
                                .toList()))
                .toList();
    }
}
