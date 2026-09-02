package za.co.trademesh.modules.transport.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransportRepository {

    Optional<TransporterProfile> findTransporterByBusinessId(UUID businessId);

    boolean saveTransporter(TransporterProfile transporter);

    Optional<Vehicle> findVehicle(UUID transporterId, UUID vehicleId);

    Optional<Vehicle> findVehicleByRequestId(UUID transporterId, UUID requestId);

    boolean saveVehicle(Vehicle vehicle);

    Optional<Driver> findDriver(UUID transporterId, UUID driverId);

    Optional<Driver> findDriverByRequestId(UUID transporterId, UUID requestId);

    boolean saveDriver(Driver driver);

    Optional<DriverVehicleAssignment> findAssignment(UUID transporterId, UUID assignmentId);

    Optional<DriverVehicleAssignment> findAssignmentByRequestId(UUID transporterId, UUID requestId);

    boolean saveAssignment(DriverVehicleAssignment assignment);

    boolean endAssignment(UUID transporterId, UUID assignmentId, UUID endedByUserId, Instant endedAt);

    List<DriverVehicleAssignment> findVehicleAssignmentHistory(UUID transporterId, UUID vehicleId);

    Optional<CapacityOffer> findOffer(UUID transporterId, UUID offerId);

    Optional<CapacityOffer> findOfferByRequestId(UUID transporterId, UUID requestId);

    boolean saveOffer(CapacityOffer offer);

    boolean cancelOffer(UUID transporterId, UUID offerId, Instant cancelledAt);

    void expireOffer(UUID transporterId, UUID offerId, Instant now);

    boolean tryReserveCapacity(UUID offerId, BigDecimal weightKg, BigDecimal volumeCubicMetres, Instant now);

    boolean releaseCapacity(UUID offerId, BigDecimal weightKg, BigDecimal volumeCubicMetres);
}
