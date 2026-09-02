package za.co.trademesh.modules.delivery.infrastructure;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.delivery.application.SupplierDistanceClient;

/** Fallback distance client: the application starts, a distance lookup fails loudly. */
@Component
@ConditionalOnProperty(
        prefix = "trademesh.delivery.search",
        name = "distance-provider",
        havingValue = "unconfigured",
        matchIfMissing = true)
class UnconfiguredSupplierDistanceClient implements SupplierDistanceClient {

    @Override
    public Map<UUID, Distance> distances(
            double originLatitude, double originLongitude, List<Destination> destinations) {
        throw new IllegalStateException(
                "No distance provider is configured; set trademesh.delivery.search.distance-provider");
    }
}
