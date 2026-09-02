package za.co.trademesh.modules.transport.domain;

import java.time.Instant;
import java.util.UUID;

public record CapacityReservation(
        UUID id,
        UUID matchSearchId,
        UUID clientRequestId,
        UUID offerId,
        Capacity reservedCapacity,
        CapacityReservationStatus status,
        Instant expiresAt,
        UUID createdByUserId,
        Instant createdAt,
        Instant releasedAt) {}
