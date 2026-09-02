package za.co.trademesh.modules.routing.domain;

import java.math.BigDecimal;

public record RouteFactorScore(
        RouteFactor factor,
        BigDecimal rawValue,
        String rawUnit,
        BigDecimal normalizedValue,
        BigDecimal weight,
        BigDecimal contribution,
        boolean dataAvailable) {}
