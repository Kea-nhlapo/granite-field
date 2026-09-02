package za.co.trademesh.modules.transport.domain;

import java.time.Instant;
import java.util.UUID;

public record TransporterProfile(
        UUID id,
        UUID businessId,
        String displayName,
        TransporterStatus status,
        UUID createdByUserId,
        Instant createdAt) {}
