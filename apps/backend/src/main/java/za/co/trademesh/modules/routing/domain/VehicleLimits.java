package za.co.trademesh.modules.routing.domain;

import java.math.BigDecimal;

public record VehicleLimits(
        BigDecimal maximumWeightKg,
        BigDecimal maximumHeightMetres,
        BigDecimal maximumWidthMetres,
        BigDecimal maximumLengthMetres) {}
