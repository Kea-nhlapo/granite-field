package za.co.trademesh.modules.transport.domain;

import java.time.Instant;
import java.util.UUID;

public record DriverVehicleAssignment(
        UUID id,
        UUID transporterId,
        UUID clientRequestId,
        UUID vehicleId,
        UUID driverId,
        Instant startedAt,
        Instant endedAt,
        UUID assignedByUserId,
        UUID endedByUserId) {

    public boolean active() {
        return endedAt == null;
    }
}
