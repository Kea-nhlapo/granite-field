package za.co.trademesh.modules.routing.application;

import java.math.BigDecimal;
import java.util.List;
import za.co.trademesh.modules.routing.domain.GeoPoint;
import za.co.trademesh.modules.routing.domain.RouteAvoidance;
import za.co.trademesh.modules.routing.domain.VehicleLimits;

/** Provider-neutral boundary for obtaining candidate routes. */
public interface RouteProvider {

    ProviderResult calculate(ProviderRequest request) throws RouteProviderException;

    record ProviderRequest(
            GeoPoint origin,
            GeoPoint destination,
            List<GeoPoint> waypoints,
            VehicleLimits vehicleLimits,
            List<RouteAvoidance> avoidances,
            int maximumCandidates) {

        public ProviderRequest {
            waypoints = List.copyOf(waypoints);
            avoidances = List.copyOf(avoidances);
        }
    }

    record ProviderResult(String providerName, String providerVersion, List<ProviderCandidate> candidates) {
        public ProviderResult {
            candidates = List.copyOf(candidates);
        }
    }

    record ProviderCandidate(
            String providerCandidateKey,
            String label,
            List<GeoPoint> geometry,
            long distanceMetres,
            long durationSeconds,
            BigDecimal tollEstimateZar,
            List<ProviderSegment> segments) {

        public ProviderCandidate {
            geometry = List.copyOf(geometry);
            segments = List.copyOf(segments);
        }
    }

    record ProviderSegment(
            int sequence,
            String fromLabel,
            String toLabel,
            List<GeoPoint> geometry,
            long distanceMetres,
            long durationSeconds,
            BigDecimal tollEstimateZar) {

        public ProviderSegment {
            geometry = List.copyOf(geometry);
        }
    }
}
