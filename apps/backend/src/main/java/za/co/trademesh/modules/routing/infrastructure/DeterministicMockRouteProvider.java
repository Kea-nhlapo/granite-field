package za.co.trademesh.modules.routing.infrastructure;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import za.co.trademesh.modules.routing.application.RouteProvider;
import za.co.trademesh.modules.routing.domain.GeoPoint;
import za.co.trademesh.modules.routing.domain.RouteAvoidance;

final class DeterministicMockRouteProvider implements RouteProvider {

    private static final double EARTH_RADIUS_METRES = 6_371_000;

    @Override
    public ProviderResult calculate(ProviderRequest request) {
        List<GeoPoint> stops = new ArrayList<>();
        stops.add(request.origin());
        stops.addAll(request.waypoints());
        stops.add(request.destination());

        List<ProviderCandidate> candidates = new ArrayList<>();
        candidates.add(candidate("mock-fast", "FASTEST", stops, 0.002, 78, tollRate(request, 0.24)));
        candidates.add(candidate("mock-low-toll", "LOW_TOLL", stops, 0.025, 66, BigDecimal.ZERO));
        candidates.add(candidate("mock-alternative", "ALTERNATIVE", stops, -0.018, 72, tollRate(request, 0.10)));
        return new ProviderResult(
                "deterministic-mock",
                "mock-route/v1",
                candidates.stream().limit(request.maximumCandidates()).toList());
    }

    private static ProviderCandidate candidate(
            String key,
            String label,
            List<GeoPoint> stops,
            double midpointOffset,
            int baseSpeedKilometresPerHour,
            BigDecimal tollRatePerKilometre) {
        List<ProviderSegment> segments = new ArrayList<>();
        List<GeoPoint> geometry = new ArrayList<>();
        long totalDistance = 0;
        long totalDuration = 0;
        BigDecimal totalToll = BigDecimal.ZERO;
        for (int index = 0; index < stops.size() - 1; index++) {
            GeoPoint from = stops.get(index);
            GeoPoint to = stops.get(index + 1);
            GeoPoint midpoint = new GeoPoint(
                    null,
                    (from.latitude() + to.latitude()) / 2 + midpointOffset,
                    (from.longitude() + to.longitude()) / 2 + midpointOffset / 2);
            List<GeoPoint> segmentGeometry = List.of(from, midpoint, to);
            long distance = pathDistance(segmentGeometry);
            long duration = Math.max(1, Math.round(distance / (baseSpeedKilometresPerHour * 1000.0 / 3600.0)));
            BigDecimal toll = BigDecimal.valueOf(distance)
                    .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP)
                    .multiply(tollRatePerKilometre)
                    .setScale(2, RoundingMode.HALF_UP);
            segments.add(
                    new ProviderSegment(index, from.label(), to.label(), segmentGeometry, distance, duration, toll));
            if (geometry.isEmpty()) {
                geometry.addAll(segmentGeometry);
            } else {
                geometry.addAll(segmentGeometry.subList(1, segmentGeometry.size()));
            }
            totalDistance += distance;
            totalDuration += duration;
            totalToll = totalToll.add(toll);
        }
        return new ProviderCandidate(
                key,
                label,
                geometry,
                totalDistance,
                totalDuration,
                totalToll.setScale(2, RoundingMode.HALF_UP),
                segments);
    }

    private static BigDecimal tollRate(ProviderRequest request, double normalRate) {
        return request.avoidances().contains(RouteAvoidance.TOLLS) ? BigDecimal.ZERO : BigDecimal.valueOf(normalRate);
    }

    static long pathDistance(List<GeoPoint> geometry) {
        double distance = 0;
        for (int index = 0; index < geometry.size() - 1; index++) {
            distance += haversine(geometry.get(index), geometry.get(index + 1));
        }
        return Math.max(1, Math.round(distance));
    }

    private static double haversine(GeoPoint from, GeoPoint to) {
        double firstLatitude = Math.toRadians(from.latitude());
        double secondLatitude = Math.toRadians(to.latitude());
        double latitudeDifference = Math.toRadians(to.latitude() - from.latitude());
        double longitudeDifference = Math.toRadians(to.longitude() - from.longitude());
        double value = Math.sin(latitudeDifference / 2) * Math.sin(latitudeDifference / 2)
                + Math.cos(firstLatitude)
                        * Math.cos(secondLatitude)
                        * Math.sin(longitudeDifference / 2)
                        * Math.sin(longitudeDifference / 2);
        return EARTH_RADIUS_METRES * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
    }
}
