package za.co.trademesh.modules.routing.infrastructure;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.routing.application.RouteFactorDataProvider;
import za.co.trademesh.modules.routing.application.RouteScoringProperties;
import za.co.trademesh.modules.routing.domain.CandidateRoute;
import za.co.trademesh.modules.routing.domain.RouteCalculation;
import za.co.trademesh.modules.routing.domain.RouteFactor;

@Component
class DeterministicRouteFactorDataProvider implements RouteFactorDataProvider {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final RouteScoringProperties properties;

    DeterministicRouteFactorDataProvider(RouteScoringProperties properties) {
        this.properties = properties;
    }

    @Override
    public Map<RouteFactor, Measurement> measure(RouteCalculation calculation, CandidateRoute candidate) {
        EnumMap<RouteFactor, Measurement> measurements = new EnumMap<>(RouteFactor.class);
        measurements.put(RouteFactor.TIME, measurement(candidate.durationSeconds()));
        measurements.put(RouteFactor.DISTANCE, measurement(candidate.distanceMetres()));
        measurements.put(RouteFactor.TOLLS, measurement(candidate.tollEstimateZar()));
        measurements.put(RouteFactor.FUEL, measurement(fuelLitres(candidate)));

        if ("deterministic-mock".equals(calculation.providerName())) {
            addLocalOperatingData(measurements, candidate.sequence());
        }
        return Map.copyOf(measurements);
    }

    private BigDecimal fuelLitres(CandidateRoute candidate) {
        BigDecimal distanceKilometres =
                BigDecimal.valueOf(candidate.distanceMetres()).movePointLeft(3);
        BigDecimal routeAdjustment =
                switch (candidate.sequence()) {
                    case 0 -> new BigDecimal("1.08");
                    case 1 -> new BigDecimal("0.94");
                    default -> BigDecimal.ONE;
                };
        return distanceKilometres
                .multiply(properties.fuelLitresPer100Km())
                .multiply(routeAdjustment)
                .divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP);
    }

    private static void addLocalOperatingData(EnumMap<RouteFactor, Measurement> measurements, int candidateSequence) {
        switch (candidateSequence) {
            case 0 -> {
                measurements.put(RouteFactor.SAFETY_EXPOSURE, measurement("78"));
                measurements.put(RouteFactor.ROAD_QUALITY, measurement("52"));
                measurements.put(RouteFactor.CONNECTIVITY, measurement("48"));
            }
            case 1 -> {
                measurements.put(RouteFactor.SAFETY_EXPOSURE, measurement("43"));
                // A missing road-quality feed is intentional: scoring must expose uncertainty.
                measurements.put(RouteFactor.CONNECTIVITY, measurement("72"));
            }
            default -> {
                measurements.put(RouteFactor.SAFETY_EXPOSURE, measurement("18"));
                measurements.put(RouteFactor.ROAD_QUALITY, measurement("91"));
                measurements.put(RouteFactor.CONNECTIVITY, measurement("94"));
            }
        }
    }

    private static Measurement measurement(long value) {
        return new Measurement(BigDecimal.valueOf(value), "ROUTE_PROVIDER");
    }

    private static Measurement measurement(String value) {
        return new Measurement(new BigDecimal(value), "DETERMINISTIC_LOCAL_DATA");
    }

    private static Measurement measurement(BigDecimal value) {
        return new Measurement(value, "DETERMINISTIC_ESTIMATE");
    }
}
