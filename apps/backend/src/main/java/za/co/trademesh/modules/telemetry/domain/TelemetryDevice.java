package za.co.trademesh.modules.telemetry.domain;

import java.time.Instant;
import java.util.UUID;

public record TelemetryDevice(
        UUID id,
        UUID businessId,
        UUID shipmentId,
        String displayName,
        String credentialHash,
        TelemetryDeviceStatus status,
        UUID createdByUserId,
        Instant createdAt,
        Instant lastSeenAt,
        Instant revokedAt) {}
