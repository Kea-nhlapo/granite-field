package za.co.trademesh.modules.routing.infrastructure;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import za.co.trademesh.modules.routing.application.RouteProvider;
import za.co.trademesh.modules.routing.domain.GeoPoint;

final class StraightLineFallbackRouteProvider implements RouteProvider {

    @Override
    public ProviderResult calculate(ProviderRequest request) {
        List<GeoPoint> geometry = new ArrayList<>();
        geometry.add(request.origin());
        geometry.addAll(request.waypoints());
        geometry.add(request.destination());
        List<ProviderSegment> segments = new ArrayList<>();
        long distance = 0;
        long duration = 0;
        for (int index = 0; index < geometry.size() - 1; index++) {
            GeoPoint from = geometry.get(index);
            GeoPoint to = geometry.get(index + 1);
            long segmentDistance = DeterministicMockRouteProvider.pathDistance(List.of(from, to));
            long segmentDuration = Math.max(1, Math.round(segmentDistance / (55_000.0 / 3600.0)));
            segments.add(new ProviderSegment(
                    index,
                    from.label(),
                    to.label(),
                    List.of(from, to),
                    segmentDistance,
                    segmentDuration,
                    BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY)));
            distance += segmentDistance;
            duration += segmentDuration;
        }
        return new ProviderResult(
                "straight-line-fallback",
                "fallback-route/v1",
                List.of(new ProviderCandidate(
                        "fallback-direct",
                        "FALLBACK",
                        geometry,
                        distance,
                        duration,
                        BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY),
                        segments)));
    }
}
