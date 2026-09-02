package za.co.trademesh.modules.insurance.domain;

import java.time.Instant;
import java.util.UUID;

public record InsuranceDecision(
        UUID id,
        UUID caseId,
        UUID commandId,
        String inputFingerprint,
        InsuranceDecisionOutcome outcome,
        String note,
        UUID decidedByUserId,
        Instant decidedAt) {}
