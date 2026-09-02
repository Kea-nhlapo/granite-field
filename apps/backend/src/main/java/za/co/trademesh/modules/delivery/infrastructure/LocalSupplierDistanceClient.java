package za.co.trademesh.modules.delivery.infrastructure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.delivery.application.SupplierDistanceClient;

@Component
@ConditionalOnProperty(
        prefix = "trademesh.delivery.search",
        name = "distance-provider",
        havingValue = "local",
        matchIfMissing = true)
class LocalSupplierDistanceClient implements SupplierDistanceClient {

    @Override
    public Map<UUID, Distance> distances(
            double originLatitude, double originLongitude, List<Destination> destinations) {
        Map<UUID, Distance> result = new LinkedHashMap<>();
        for (int index = 0; index < destinations.size(); index++) {
            long metres = 2_000L + (index * 1_500L);
            result.put(destinations.get(index).supplierProfileId(), new Distance(metres, Math.max(60, metres / 8)));
        }
        return Map.copyOf(result);
    }
}
