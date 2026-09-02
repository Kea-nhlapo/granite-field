package za.co.trademesh.modules.transport.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Vehicle(
        UUID id,
        UUID transporterId,
        UUID clientRequestId,
        String registrationNumber,
        String description,
        BigDecimal maximumWeightKg,
        BigDecimal maximumVolumeCubicMetres,
        VehicleStatus status,
        UUID createdByUserId,
        Instant createdAt) {}
