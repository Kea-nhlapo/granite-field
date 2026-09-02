package za.co.trademesh.modules.delivery.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface SupplierDistanceClient {

    Map<UUID, Distance> distances(double originLatitude, double originLongitude, List<Destination> destinations);

    record Destination(UUID supplierProfileId, String address) {}

    record Distance(long metres, long durationSeconds) {}
}
