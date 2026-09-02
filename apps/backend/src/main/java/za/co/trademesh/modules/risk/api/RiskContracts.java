package za.co.trademesh.modules.risk.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.trademesh.modules.risk.application.RiskService;
import za.co.trademesh.modules.risk.domain.RiskEvidenceReference;
import za.co.trademesh.modules.risk.domain.RiskIndicator;
import za.co.trademesh.modules.risk.domain.RiskIndicatorState;
import za.co.trademesh.modules.risk.domain.RiskIndicatorTransition;
import za.co.trademesh.modules.risk.domain.RiskRule;
import za.co.trademesh.modules.risk.domain.RiskSeverity;

final class RiskContracts {

    private RiskContracts() {}

    record TransitionRequest(
            @NotNull UUID commandId,
            @NotNull RiskIndicatorState targetState,
            @NotBlank @Size(max = 1000) String note) {

        RiskService.TransitionCommand toCommand() {
            return new RiskService.TransitionCommand(commandId, targetState, note);
        }
    }

    record IndicatorListResponse(List<IndicatorResponse> indicators) {
        static IndicatorListResponse from(List<RiskIndicator> indicators) {
            return new IndicatorListResponse(
                    indicators.stream().map(IndicatorResponse::from).toList());
        }
    }

    record IndicatorResponse(
            UUID indicatorId,
            UUID shipmentId,
            RiskRule rule,
            String ruleVersion,
            RiskSeverity severity,
            String explanation,
            RiskIndicatorState state,
            Instant firstObservedAt,
            Instant lastObservedAt,
            Instant createdAt,
            Instant updatedAt,
            List<EvidenceResponse> evidence,
            List<TransitionResponse> reviewHistory) {

        static IndicatorResponse from(RiskIndicator indicator) {
            return new IndicatorResponse(
                    indicator.id(),
                    indicator.shipmentId(),
                    indicator.rule(),
                    indicator.ruleVersion(),
                    indicator.severity(),
                    indicator.explanation(),
                    indicator.state(),
                    indicator.firstObservedAt(),
                    indicator.lastObservedAt(),
                    indicator.createdAt(),
                    indicator.updatedAt(),
                    indicator.evidence().stream().map(EvidenceResponse::from).toList(),
                    indicator.transitions().stream()
                            .map(TransitionResponse::from)
                            .toList());
        }
    }

    record EvidenceResponse(String type, UUID referenceId, Instant observedAt) {
        static EvidenceResponse from(RiskEvidenceReference evidence) {
            return new EvidenceResponse(evidence.evidenceType(), evidence.evidenceId(), evidence.observedAt());
        }
    }

    record TransitionResponse(
            UUID transitionId,
            RiskIndicatorState fromState,
            RiskIndicatorState toState,
            UUID actorUserId,
            String note,
            Instant occurredAt) {

        static TransitionResponse from(RiskIndicatorTransition transition) {
            return new TransitionResponse(
                    transition.id(),
                    transition.fromState(),
                    transition.toState(),
                    transition.actorUserId(),
                    transition.note(),
                    transition.occurredAt());
        }
    }
}
