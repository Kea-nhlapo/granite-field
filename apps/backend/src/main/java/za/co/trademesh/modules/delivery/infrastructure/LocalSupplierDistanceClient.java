package za.co.trademesh.modules.delivery.infrastructure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.delivery.application.SupplierDistanceClient;

@Component
@ConditionalOnProperty(prefix = "trademesh.delivery.search", name = "distance-provider", havingValue = "local")
class LocalSupplierDistanceClient implements SupplierDistanceClient {

    @Override
    public Map<UUID, Distance> distances(
            double originLatitude, double originLongitude, List<Destination> destinations) {
        Map<UUID, Distance> result = new LinkedHashMap<>();
        for (int index = 0; index < destinations.size(); index++) {
            long metres = coordinateDistance(
                            originLatitude,
                            originLongitude,
                            destinations.get(index).address())
                    .orElse(2_000L + (index * 1_500L));
            result.put(destinations.get(index).supplierProfileId(), new Distance(metres, Math.max(60, metres / 8)));
        }
        return Map.copyOf(result);
    }

    private static java.util.Optional<Long> coordinateDistance(
            double originLatitude, double originLongitude, String destination) {
        if (destination == null) {
            return java.util.Optional.empty();
        }
        String[] parts = destination.split(",", 2);
        if (parts.length != 2) {
            return java.util.Optional.empty();
        }
        try {
            double latitude = Double.parseDouble(parts[0].strip());
            double longitude = Double.parseDouble(parts[1].strip());
            if (!Double.isFinite(latitude)
                    || latitude < -90
                    || latitude > 90
                    || !Double.isFinite(longitude)
                    || longitude < -180
                    || longitude > 180) {
                return java.util.Optional.empty();
            }
            double firstLatitude = Math.toRadians(originLatitude);
            double secondLatitude = Math.toRadians(latitude);
            double latitudeDifference = Math.toRadians(latitude - originLatitude);
            double longitudeDifference = Math.toRadians(longitude - originLongitude);
            double value = Math.sin(latitudeDifference / 2) * Math.sin(latitudeDifference / 2)
                    + Math.cos(firstLatitude)
                            * Math.cos(secondLatitude)
                            * Math.sin(longitudeDifference / 2)
                            * Math.sin(longitudeDifference / 2);
            long roadAdjusted =
                    Math.max(1, Math.round(6_371_000 * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value)) * 1.18));
            return java.util.Optional.of(roadAdjusted);
        } catch (NumberFormatException invalidCoordinate) {
            return java.util.Optional.empty();
        }
    }
}
