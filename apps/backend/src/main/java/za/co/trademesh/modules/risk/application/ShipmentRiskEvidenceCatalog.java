package za.co.trademesh.modules.risk.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Internal risk facts for a purpose-scoped evidence view. */
public interface ShipmentRiskEvidenceCatalog {

    List<RiskIndicator> find(UUID shipmentId);

    record RiskIndicator(
            UUID indicatorId,
            String rule,
            String ruleVersion,
            String severity,
            String explanation,
            String state,
            Instant firstObservedAt,
            Instant lastObservedAt,
            List<EvidenceReference> evidence) {
        public RiskIndicator {
            evidence = List.copyOf(evidence);
        }
    }

    record EvidenceReference(String type, UUID id, Instant observedAt) {}
}
