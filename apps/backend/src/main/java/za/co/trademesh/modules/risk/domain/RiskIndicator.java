package za.co.trademesh.modules.risk.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RiskIndicator(
        UUID id,
        UUID shipmentId,
        UUID businessId,
        RiskRule rule,
        String ruleVersion,
        RiskSeverity severity,
        String explanation,
        RiskIndicatorState state,
        Instant firstObservedAt,
        Instant lastObservedAt,
        Instant createdAt,
        Instant updatedAt,
        List<RiskEvidenceReference> evidence,
        List<RiskIndicatorTransition> transitions) {

    public RiskIndicator {
        evidence = List.copyOf(evidence);
        transitions = List.copyOf(transitions);
    }
}
