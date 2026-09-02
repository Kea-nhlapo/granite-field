package za.co.trademesh.modules.routing.infrastructure;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import za.co.trademesh.modules.routing.application.EncodedPolyline;
import za.co.trademesh.modules.routing.application.RouteProvider;
import za.co.trademesh.modules.routing.application.RouteProviderException;
import za.co.trademesh.modules.routing.application.RoutingProviderProperties;
import za.co.trademesh.modules.routing.domain.GeoPoint;
import za.co.trademesh.modules.routing.domain.RouteAvoidance;

final class GoogleDirectionsRouteProvider implements RouteProvider {

    private final RestClient client;
    private final String mapsApiKey;

    GoogleDirectionsRouteProvider(RestClient.Builder builder, RoutingProviderProperties properties) {
        if (properties.directionsEndpoint() == null
                || properties.directionsEndpoint().isBlank()
                || properties.mapsApiKey() == null
                || properties.mapsApiKey().isBlank()) {
            throw new IllegalStateException("Google Directions endpoint and API key are required");
        }
        this.client = builder.baseUrl(properties.directionsEndpoint().strip()).build();
        this.mapsApiKey = properties.mapsApiKey().strip();
    }

    @Override
    public ProviderResult calculate(ProviderRequest request) throws RouteProviderException {
        try {
            DirectionsResponse response = client.get()
                    .uri(builder -> {
                        builder.queryParam("origin", coordinate(request.origin()))
                                .queryParam("destination", coordinate(request.destination()))
                                .queryParam("alternatives", request.waypoints().isEmpty())
                                .queryParam("units", "metric")
                                .queryParam("key", mapsApiKey);
                        if (!request.waypoints().isEmpty()) {
                            builder.queryParam(
                                    "waypoints",
                                    request.waypoints().stream()
                                            .map(GoogleDirectionsRouteProvider::coordinate)
                                            .collect(Collectors.joining("|")));
                        }
                        String avoid = avoidances(request.avoidances());
                        if (!avoid.isBlank()) {
                            builder.queryParam("avoid", avoid);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .body(DirectionsResponse.class);
            return map(response, request);
        } catch (RestClientException providerFailure) {
            throw new RouteProviderException(
                    "GOOGLE_DIRECTIONS_UNAVAILABLE",
                    "Google Directions is temporarily unavailable.",
                    true,
                    providerFailure);
        } catch (RuntimeException invalidResponse) {
            throw new RouteProviderException(
                    "GOOGLE_DIRECTIONS_INVALID_RESPONSE",
                    "Google Directions returned an unusable route.",
                    false,
                    invalidResponse);
        }
    }

    private static ProviderResult map(DirectionsResponse response, ProviderRequest request)
            throws RouteProviderException {
        if (response == null || !"OK".equals(response.status()) || response.routes() == null) {
            String status = response == null ? "EMPTY" : String.valueOf(response.status());
            boolean retryable = "UNKNOWN_ERROR".equals(status) || "OVER_QUERY_LIMIT".equals(status);
            throw new RouteProviderException(
                    "GOOGLE_DIRECTIONS_" + safeStatus(status),
                    "Google Directions could not calculate this route.",
                    retryable);
        }
        List<ProviderCandidate> candidates = new ArrayList<>();
        for (int routeIndex = 0;
                routeIndex < Math.min(response.routes().size(), request.maximumCandidates());
                routeIndex++) {
            Route route = response.routes().get(routeIndex);
            if (route == null
                    || route.overviewPolyline() == null
                    || route.overviewPolyline().points() == null
                    || route.legs() == null
                    || route.legs().isEmpty()) {
                continue;
            }
            List<GeoPoint> geometry = new ArrayList<>(
                    EncodedPolyline.decode(route.overviewPolyline().points()));
            geometry.set(0, request.origin());
            geometry.set(geometry.size() - 1, request.destination());
            List<ProviderSegment> segments = new ArrayList<>();
            long totalDistance = 0;
            long totalDuration = 0;
            for (int legIndex = 0; legIndex < route.legs().size(); legIndex++) {
                Leg leg = route.legs().get(legIndex);
                if (leg == null || leg.distance() == null || leg.duration() == null) {
                    throw new IllegalArgumentException("Google route leg is incomplete");
                }
                GeoPoint from =
                        legIndex == 0 ? request.origin() : request.waypoints().get(legIndex - 1);
                GeoPoint to = legIndex < request.waypoints().size()
                        ? request.waypoints().get(legIndex)
                        : request.destination();
                long distance = leg.distance().value();
                long duration = leg.duration().value();
                if (distance <= 0 || duration <= 0) {
                    throw new IllegalArgumentException("Google route leg has invalid totals");
                }
                totalDistance = Math.addExact(totalDistance, distance);
                totalDuration = Math.addExact(totalDuration, duration);
                segments.add(new ProviderSegment(
                        legIndex,
                        text(leg.startAddress(), from.label()),
                        text(leg.endAddress(), to.label()),
                        List.of(from, to),
                        distance,
                        duration,
                        new BigDecimal("0.00")));
            }
            String label = route.summary() == null || route.summary().isBlank()
                    ? "GOOGLE_ROUTE_" + (routeIndex + 1)
                    : route.summary().strip();
            candidates.add(new ProviderCandidate(
                    "google-" + routeIndex,
                    label,
                    List.copyOf(geometry),
                    totalDistance,
                    totalDuration,
                    new BigDecimal("0.00"),
                    List.copyOf(segments)));
        }
        if (candidates.isEmpty()) {
            throw new RouteProviderException(
                    "GOOGLE_DIRECTIONS_NO_ROUTES", "Google Directions returned no usable routes.", false);
        }
        return new ProviderResult("google-directions", "directions-v1", candidates);
    }

    private static String coordinate(GeoPoint point) {
        return point.latitude() + "," + point.longitude();
    }

    private static String avoidances(List<RouteAvoidance> avoidances) {
        return avoidances.stream()
                .filter(avoidance -> avoidance != RouteAvoidance.UNPAVED_ROADS)
                .map(avoidance -> avoidance.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining("|"));
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static String safeStatus(String value) {
        String normalized = value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
        return normalized.isBlank() ? "UNKNOWN" : normalized;
    }

    private record DirectionsResponse(String status, List<Route> routes) {}

    private record Route(
            String summary,

            @com.fasterxml.jackson.annotation.JsonProperty("overview_polyline")
            Polyline overviewPolyline,

            List<Leg> legs) {}

    private record Polyline(String points) {}

    private record Leg(
            @com.fasterxml.jackson.annotation.JsonProperty("start_address")
            String startAddress,

            @com.fasterxml.jackson.annotation.JsonProperty("end_address")
            String endAddress,

            Measure distance,
            Measure duration) {}

    private record Measure(long value) {}
}
