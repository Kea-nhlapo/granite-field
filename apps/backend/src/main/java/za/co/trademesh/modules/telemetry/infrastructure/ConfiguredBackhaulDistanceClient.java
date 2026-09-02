package za.co.trademesh.modules.telemetry.infrastructure;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.delivery.application.SupplierDistanceClient;
import za.co.trademesh.modules.telemetry.application.BackhaulDistanceClient;

@Component
class ConfiguredBackhaulDistanceClient implements BackhaulDistanceClient {

    private final SupplierDistanceClient distances;

    ConfiguredBackhaulDistanceClient(SupplierDistanceClient distances) {
        this.distances = distances;
    }

    @Override
    public Map<UUID, Distance> distances(double latitude, double longitude, List<Pickup> pickups) {
        Map<UUID, SupplierDistanceClient.Distance> measured = distances.distances(
                latitude,
                longitude,
                pickups.stream()
                        .map(pickup -> new SupplierDistanceClient.Destination(
                                pickup.shipmentId(), pickup.latitude() + "," + pickup.longitude()))
                        .toList());
        return measured.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> new Distance(
                                entry.getValue().metres(), entry.getValue().durationSeconds())));
    }
}
