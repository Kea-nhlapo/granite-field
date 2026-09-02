package za.co.trademesh.modules.risk.domain;

import java.time.Instant;
import java.util.UUID;

public record RiskIndicatorTransition(
        UUID id,
        UUID indicatorId,
        UUID commandId,
        String inputFingerprint,
        RiskIndicatorState fromState,
        RiskIndicatorState toState,
        UUID actorUserId,
        String note,
        Instant occurredAt) {}
