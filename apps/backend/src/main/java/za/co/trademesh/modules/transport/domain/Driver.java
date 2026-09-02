package za.co.trademesh.modules.transport.domain;

import java.time.Instant;
import java.util.UUID;

public record Driver(
        UUID id,
        UUID transporterId,
        UUID clientRequestId,
        String displayName,
        String driverReference,
        DriverStatus status,
        UUID createdByUserId,
        Instant createdAt) {}
