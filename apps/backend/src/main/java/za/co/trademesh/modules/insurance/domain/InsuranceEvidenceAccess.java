package za.co.trademesh.modules.insurance.domain;

import java.time.Instant;
import java.util.UUID;

public record InsuranceEvidenceAccess(
        UUID id,
        UUID caseId,
        UUID shipmentId,
        UUID actorUserId,
        InsurancePurpose purpose,
        InsuranceAccessOutcome outcome,
        String reason,
        UUID correlationId,
        Instant occurredAt) {}
