package za.co.trademesh.modules.shipment.domain;

import java.time.Instant;
import java.util.UUID;

public record ShipmentTransition(
        UUID id,
        UUID commandId,
        String inputFingerprint,
        ShipmentStatus fromStatus,
        ShipmentStatus toStatus,
        UUID actorUserId,
        Instant occurredAt,
        String reason,
        UUID correlationId,
        ShipmentActionSource source) {}
