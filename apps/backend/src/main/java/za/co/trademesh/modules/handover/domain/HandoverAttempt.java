package za.co.trademesh.modules.handover.domain;

import java.time.Instant;
import java.util.UUID;

public record HandoverAttempt(
        UUID id,
        UUID challengeId,
        UUID actorUserId,
        HandoverAttemptOutcome outcome,
        Instant attemptedAt,
        Instant observedAt,
        Double latitude,
        Double longitude,
        String detail) {}
