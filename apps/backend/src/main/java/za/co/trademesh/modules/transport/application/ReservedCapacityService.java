package za.co.trademesh.modules.transport.application;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.transport.domain.CapacityMatchStatus;
import za.co.trademesh.modules.transport.domain.CapacityMatchingRepository;
import za.co.trademesh.modules.transport.domain.CapacityOfferStatus;
import za.co.trademesh.modules.transport.domain.CapacityReservationStatus;
import za.co.trademesh.modules.transport.domain.DriverStatus;
import za.co.trademesh.modules.transport.domain.TransportRepository;
import za.co.trademesh.modules.transport.domain.VehicleStatus;

@Service
class ReservedCapacityService implements ReservedCapacityCatalog {

    private final CapacityMatchingRepository matches;
    private final TransportRepository transport;
    private final Clock clock;

    ReservedCapacityService(CapacityMatchingRepository matches, TransportRepository transport, Clock clock) {
        this.matches = matches;
        this.transport = transport;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Optional<ReservedCapacity> claimReserved(UUID requestedByBusinessId, UUID searchId, UUID reservationId) {
        var now = clock.instant();
        var search = matches.findSearchForUpdate(requestedByBusinessId, searchId);
        if (search.isEmpty() || search.get().status() != CapacityMatchStatus.RESERVED) {
            return Optional.empty();
        }
        var reservation = matches.findReservationForUpdate(reservationId);
        if (reservation.isEmpty()
                || !reservation.get().id().equals(reservationId)
                || !reservation.get().matchSearchId().equals(searchId)
                || reservation.get().status() != CapacityReservationStatus.ACTIVE
                || !reservation.get().expiresAt().isAfter(now)) {
            return Optional.empty();
        }
        var candidate = matches.findCandidate(searchId, reservation.get().offerId());
        if (candidate.isEmpty() || !candidate.get().compatible()) {
            return Optional.empty();
        }
        var offer = transport.findOffer(
                candidate.get().transporterId(), reservation.get().offerId());
        if (offer.isEmpty()
                || offer.get().status() != CapacityOfferStatus.ACTIVE
                || !offer.get().expiresAt().isAfter(now)) {
            return Optional.empty();
        }
        var assignment = findActiveAssignment(
                candidate.get().transporterId(), offer.get().driverAssignmentId());
        if (assignment.isEmpty()
                || !matches.markReservationConsumed(reservationId, now)
                || !matches.markSearchStatus(searchId, CapacityMatchStatus.RESERVED, CapacityMatchStatus.ASSIGNED)) {
            return Optional.empty();
        }
        return assignment.map(value -> new ReservedCapacity(
                searchId,
                reservationId,
                search.get().demandGroupSuggestionId(),
                offer.get().id(),
                candidate.get().transporterId(),
                reservation.get().reservedCapacity().weightKg(),
                reservation.get().reservedCapacity().volumeCubicMetres(),
                reservation.get().expiresAt(),
                value));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TransportAssignment> findActiveAssignment(UUID transporterId, UUID assignmentId) {
        var assignment = transport.findAssignment(transporterId, assignmentId);
        if (assignment.isEmpty() || !assignment.get().active()) {
            return Optional.empty();
        }
        var vehicle = transport.findVehicle(transporterId, assignment.get().vehicleId());
        var driver = transport.findDriver(transporterId, assignment.get().driverId());
        if (vehicle.isEmpty()
                || vehicle.get().status() != VehicleStatus.ACTIVE
                || driver.isEmpty()
                || driver.get().status() != DriverStatus.ACTIVE) {
            return Optional.empty();
        }
        return Optional.of(new TransportAssignment(
                transporterId,
                assignmentId,
                vehicle.get().id(),
                vehicle.get().registrationNumber(),
                vehicle.get().description(),
                vehicle.get().maximumWeightKg(),
                vehicle.get().maximumVolumeCubicMetres(),
                driver.get().id(),
                driver.get().displayName(),
                driver.get().driverReference()));
    }
}
