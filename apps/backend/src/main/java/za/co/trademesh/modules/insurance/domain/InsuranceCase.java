package za.co.trademesh.modules.insurance.domain;

import java.time.Instant;
import java.util.UUID;

public record InsuranceCase(
        UUID id,
        UUID clientRequestId,
        String inputFingerprint,
        UUID shipmentId,
        UUID businessId,
        InsurancePurpose purpose,
        UUID assignedInsurerUserId,
        UUID createdByUserId,
        Instant createdAt) {}
