package za.co.trademesh.modules.handover.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DeliveryDisputeResolution(
        UUID id,
        UUID shipmentId,
        UUID businessId,
        UUID commandId,
        String inputFingerprint,
        BigDecimal resolvedAmount,
        UUID resolvedByUserId,
        Instant resolvedAt) {}
