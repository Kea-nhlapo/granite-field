package za.co.trademesh.modules.telemetry.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface BackhaulDistanceClient {

    Map<UUID, Distance> distances(double latitude, double longitude, List<Pickup> pickups);

    record Pickup(UUID shipmentId, double latitude, double longitude) {}

    record Distance(long metres, long durationSeconds) {}
}
