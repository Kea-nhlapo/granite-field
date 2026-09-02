package za.co.trademesh.modules.transport.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.transport.domain.Capacity;
import za.co.trademesh.modules.transport.domain.CapacityOffer;
import za.co.trademesh.modules.transport.domain.CapacityOfferStatus;
import za.co.trademesh.modules.transport.domain.CargoRestriction;
import za.co.trademesh.modules.transport.domain.Driver;
import za.co.trademesh.modules.transport.domain.DriverStatus;
import za.co.trademesh.modules.transport.domain.DriverVehicleAssignment;
import za.co.trademesh.modules.transport.domain.RoutePoint;
import za.co.trademesh.modules.transport.domain.TransportRepository;
import za.co.trademesh.modules.transport.domain.TransporterProfile;
import za.co.trademesh.modules.transport.domain.TransporterStatus;
import za.co.trademesh.modules.transport.domain.Vehicle;
import za.co.trademesh.modules.transport.domain.VehicleStatus;
import za.co.trademesh.modules.transport.events.TransportEvent;
import za.co.trademesh.shared.events.DomainEvents;

@Service
public class TransportService {

    private static final int MAX_ROUTE_POINTS = 100;
    private static final int MAX_TEXT = 255;

    private final TransportRepository repository;
    private final DomainEvents events;
    private final Clock clock;

    public TransportService(TransportRepository repository, DomainEvents events, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public TransporterProfile registerTransporter(UUID businessId, String displayName, UUID actorUserId) {
        String normalizedName = requiredText(displayName, MAX_TEXT);
        var existing = repository.findTransporterByBusinessId(businessId);
        if (existing.isPresent()) {
            if (!existing.get().displayName().equals(normalizedName)) {
                throw TransportException.requestConflict();
            }
            return existing.get();
        }
        Instant now = databaseTime(clock.instant());
        TransporterProfile transporter = new TransporterProfile(
                UUID.randomUUID(),
                requiredId(businessId),
                normalizedName,
                TransporterStatus.ACTIVE,
                requiredId(actorUserId),
                now);
        if (!repository.saveTransporter(transporter)) {
            return repository
                    .findTransporterByBusinessId(businessId)
                    .filter(saved -> saved.displayName().equals(normalizedName))
                    .orElseThrow(TransportException::requestConflict);
        }
        events.publish(new TransportEvent.TransporterRegistered(transporter.id(), businessId), actorUserId.toString());
        return transporter;
    }

    @Transactional(readOnly = true)
    public TransporterProfile getTransporter(UUID businessId) {
        return requireTransporter(businessId);
    }

    @Transactional
    public Vehicle createVehicle(UUID businessId, CreateVehicle command, UUID actorUserId) {
        TransporterProfile transporter = requireTransporter(businessId);
        CreateVehicle normalized = normalize(command);
        var existing = repository.findVehicleByRequestId(transporter.id(), normalized.requestId());
        if (existing.isPresent()) {
            return sameVehicle(existing.get(), normalized);
        }
        Vehicle vehicle = new Vehicle(
                UUID.randomUUID(),
                transporter.id(),
                normalized.requestId(),
                normalized.registrationNumber(),
                normalized.description(),
                normalized.maximumWeightKg(),
                normalized.maximumVolumeCubicMetres(),
                VehicleStatus.ACTIVE,
                requiredId(actorUserId),
                databaseTime(clock.instant()));
        if (!repository.saveVehicle(vehicle)) {
            return repository
                    .findVehicleByRequestId(transporter.id(), normalized.requestId())
                    .map(saved -> sameVehicle(saved, normalized))
                    .orElseThrow(TransportException::requestConflict);
        }
        return vehicle;
    }

    @Transactional(readOnly = true)
    public Vehicle getVehicle(UUID businessId, UUID vehicleId) {
        TransporterProfile transporter = requireTransporter(businessId);
        return repository.findVehicle(transporter.id(), vehicleId).orElseThrow(TransportException::vehicleNotFound);
    }

    @Transactional
    public Driver createDriver(UUID businessId, CreateDriver command, UUID actorUserId) {
        TransporterProfile transporter = requireTransporter(businessId);
        CreateDriver normalized = normalize(command);
        var existing = repository.findDriverByRequestId(transporter.id(), normalized.requestId());
        if (existing.isPresent()) {
            return sameDriver(existing.get(), normalized);
        }
        Driver driver = new Driver(
                UUID.randomUUID(),
                transporter.id(),
                normalized.requestId(),
                normalized.displayName(),
                normalized.driverReference(),
                DriverStatus.ACTIVE,
                requiredId(actorUserId),
                databaseTime(clock.instant()));
        if (!repository.saveDriver(driver)) {
            return repository
                    .findDriverByRequestId(transporter.id(), normalized.requestId())
                    .map(saved -> sameDriver(saved, normalized))
                    .orElseThrow(TransportException::requestConflict);
        }
        return driver;
    }

    @Transactional(readOnly = true)
    public Driver getDriver(UUID businessId, UUID driverId) {
        TransporterProfile transporter = requireTransporter(businessId);
        return repository.findDriver(transporter.id(), driverId).orElseThrow(TransportException::driverNotFound);
    }

    @Transactional
    public DriverVehicleAssignment assignDriver(UUID businessId, AssignDriver command, UUID actorUserId) {
        TransporterProfile transporter = requireTransporter(businessId);
        AssignDriver normalized = normalize(command);
        var existing = repository.findAssignmentByRequestId(transporter.id(), normalized.requestId());
        if (existing.isPresent()) {
            return sameAssignment(existing.get(), normalized);
        }
        requireVehicle(transporter.id(), normalized.vehicleId());
        requireDriver(transporter.id(), normalized.driverId());
        DriverVehicleAssignment assignment = new DriverVehicleAssignment(
                UUID.randomUUID(),
                transporter.id(),
                normalized.requestId(),
                normalized.vehicleId(),
                normalized.driverId(),
                databaseTime(clock.instant()),
                null,
                requiredId(actorUserId),
                null);
        if (!repository.saveAssignment(assignment)) {
            return repository
                    .findAssignmentByRequestId(transporter.id(), normalized.requestId())
                    .map(saved -> sameAssignment(saved, normalized))
                    .orElseThrow(TransportException::assignmentConflict);
        }
        events.publish(
                new TransportEvent.DriverAssigned(
                        assignment.id(), transporter.id(), assignment.vehicleId(), assignment.driverId()),
                actorUserId.toString());
        return assignment;
    }

    @Transactional
    public DriverVehicleAssignment endAssignment(UUID businessId, UUID assignmentId, UUID actorUserId) {
        TransporterProfile transporter = requireTransporter(businessId);
        DriverVehicleAssignment assignment = repository
                .findAssignment(transporter.id(), assignmentId)
                .orElseThrow(TransportException::assignmentNotFound);
        if (!assignment.active()) {
            return assignment;
        }
        Instant now = databaseTime(clock.instant());
        if (!repository.endAssignment(transporter.id(), assignmentId, actorUserId, now)) {
            throw TransportException.assignmentConflict();
        }
        return repository
                .findAssignment(transporter.id(), assignmentId)
                .orElseThrow(TransportException::assignmentNotFound);
    }

    @Transactional(readOnly = true)
    public List<DriverVehicleAssignment> vehicleAssignmentHistory(UUID businessId, UUID vehicleId) {
        TransporterProfile transporter = requireTransporter(businessId);
        requireVehicle(transporter.id(), vehicleId);
        return repository.findVehicleAssignmentHistory(transporter.id(), vehicleId);
    }

    @Transactional
    public CapacityOffer publishOffer(UUID businessId, PublishCapacityOffer command, UUID actorUserId) {
        TransporterProfile transporter = requireTransporter(businessId);
        Instant now = databaseTime(clock.instant());
        PublishCapacityOffer normalized = normalize(command, now);
        var existing = repository.findOfferByRequestId(transporter.id(), normalized.requestId());
        if (existing.isPresent()) {
            return sameOffer(existing.get(), normalized);
        }
        Vehicle vehicle = requireVehicle(transporter.id(), normalized.vehicleId());
        DriverVehicleAssignment assignment = repository
                .findAssignment(transporter.id(), normalized.driverAssignmentId())
                .orElseThrow(TransportException::assignmentNotFound);
        if (!assignment.active() || !assignment.vehicleId().equals(vehicle.id())) {
            throw TransportException.assignmentConflict();
        }
        if (normalized.capacity().weightKg().compareTo(vehicle.maximumWeightKg()) > 0
                || normalized.capacity().volumeCubicMetres().compareTo(vehicle.maximumVolumeCubicMetres()) > 0) {
            throw TransportException.invalidOffer();
        }
        CapacityOffer offer = new CapacityOffer(
                UUID.randomUUID(),
                transporter.id(),
                normalized.requestId(),
                vehicle.id(),
                assignment.id(),
                normalized.routePoints(),
                normalized.corridorRadiusMetres(),
                normalized.departureWindowStart(),
                normalized.departureWindowEnd(),
                normalized.expiresAt(),
                normalized.restrictions(),
                normalized.capacity(),
                normalized.capacity(),
                CapacityOfferStatus.ACTIVE,
                requiredId(actorUserId),
                now,
                null);
        if (!repository.saveOffer(offer)) {
            return repository
                    .findOfferByRequestId(transporter.id(), normalized.requestId())
                    .map(saved -> sameOffer(saved, normalized))
                    .orElseThrow(TransportException::requestConflict);
        }
        events.publish(
                new TransportEvent.CapacityOfferPublished(offer.id(), transporter.id(), vehicle.id()),
                actorUserId.toString());
        return offer;
    }

    @Transactional
    public CapacityOffer getOffer(UUID businessId, UUID offerId) {
        TransporterProfile transporter = requireTransporter(businessId);
        repository.expireOffer(transporter.id(), offerId, databaseTime(clock.instant()));
        return repository.findOffer(transporter.id(), offerId).orElseThrow(TransportException::offerNotFound);
    }

    @Transactional
    public CapacityOffer cancelOffer(UUID businessId, UUID offerId, UUID actorUserId) {
        TransporterProfile transporter = requireTransporter(businessId);
        Instant now = databaseTime(clock.instant());
        repository.expireOffer(transporter.id(), offerId, now);
        CapacityOffer offer =
                repository.findOffer(transporter.id(), offerId).orElseThrow(TransportException::offerNotFound);
        if (offer.status() == CapacityOfferStatus.CANCELLED) {
            return offer;
        }
        if (offer.status() != CapacityOfferStatus.ACTIVE || !repository.cancelOffer(transporter.id(), offerId, now)) {
            throw TransportException.offerStateConflict();
        }
        events.publish(new TransportEvent.CapacityOfferCancelled(offerId, transporter.id()), actorUserId.toString());
        return repository.findOffer(transporter.id(), offerId).orElseThrow(TransportException::offerNotFound);
    }

    private TransporterProfile requireTransporter(UUID businessId) {
        return repository
                .findTransporterByBusinessId(requiredId(businessId))
                .orElseThrow(TransportException::transporterNotFound);
    }

    private Vehicle requireVehicle(UUID transporterId, UUID vehicleId) {
        return repository
                .findVehicle(transporterId, requiredId(vehicleId))
                .orElseThrow(TransportException::vehicleNotFound);
    }

    private Driver requireDriver(UUID transporterId, UUID driverId) {
        return repository
                .findDriver(transporterId, requiredId(driverId))
                .orElseThrow(TransportException::driverNotFound);
    }

    private static CreateVehicle normalize(CreateVehicle command) {
        if (command == null || command.requestId() == null) {
            throw TransportException.invalidAsset();
        }
        return new CreateVehicle(
                command.requestId(),
                requiredText(command.registrationNumber(), 32).toUpperCase(Locale.ROOT),
                requiredText(command.description(), MAX_TEXT),
                positiveCapacity(command.maximumWeightKg(), TransportException.invalidAsset()),
                positiveCapacity(command.maximumVolumeCubicMetres(), TransportException.invalidAsset()));
    }

    private static CreateDriver normalize(CreateDriver command) {
        if (command == null || command.requestId() == null) {
            throw TransportException.invalidAsset();
        }
        return new CreateDriver(
                command.requestId(),
                requiredText(command.displayName(), MAX_TEXT),
                requiredText(command.driverReference(), 100).toUpperCase(Locale.ROOT));
    }

    private static AssignDriver normalize(AssignDriver command) {
        if (command == null
                || command.requestId() == null
                || command.vehicleId() == null
                || command.driverId() == null) {
            throw TransportException.invalidAsset();
        }
        return command;
    }

    private static PublishCapacityOffer normalize(PublishCapacityOffer command, Instant now) {
        if (command == null
                || command.requestId() == null
                || command.vehicleId() == null
                || command.driverAssignmentId() == null
                || command.routePoints() == null
                || command.routePoints().size() < 2
                || command.routePoints().size() > MAX_ROUTE_POINTS
                || command.corridorRadiusMetres() < 1
                || command.corridorRadiusMetres() > 250_000
                || command.departureWindowStart() == null
                || command.departureWindowEnd() == null
                || command.expiresAt() == null
                || command.capacity() == null
                || command.restrictions() == null) {
            throw TransportException.invalidOffer();
        }
        Instant start = databaseTime(command.departureWindowStart());
        Instant end = databaseTime(command.departureWindowEnd());
        Instant expiry = databaseTime(command.expiresAt());
        if (!start.isAfter(now) || !end.isAfter(start) || !expiry.isAfter(now) || expiry.isAfter(end)) {
            throw TransportException.invalidOffer();
        }
        List<RoutePoint> points = command.routePoints().stream()
                .map(point -> {
                    if (point == null
                            || !Double.isFinite(point.latitude())
                            || point.latitude() < -90
                            || point.latitude() > 90
                            || !Double.isFinite(point.longitude())
                            || point.longitude() < -180
                            || point.longitude() > 180) {
                        throw TransportException.invalidOffer();
                    }
                    return new RoutePoint(
                            0, optionalText(point.label(), MAX_TEXT), point.latitude(), point.longitude());
                })
                .toList();
        RoutePoint firstPoint = points.get(0);
        boolean hasDistinctDestination = points.stream()
                .skip(1)
                .anyMatch(point -> Double.compare(point.latitude(), firstPoint.latitude()) != 0
                        || Double.compare(point.longitude(), firstPoint.longitude()) != 0);
        if (!hasDistinctDestination) {
            throw TransportException.invalidOffer();
        }
        List<RoutePoint> sequenced = java.util.stream.IntStream.range(0, points.size())
                .mapToObj(index -> new RoutePoint(
                        index,
                        points.get(index).label(),
                        points.get(index).latitude(),
                        points.get(index).longitude()))
                .toList();
        if (command.restrictions().stream().anyMatch(Objects::isNull)) {
            throw TransportException.invalidOffer();
        }
        List<CargoRestriction> restrictions = new HashSet<>(command.restrictions())
                .stream().sorted(Comparator.comparing(Enum::name)).toList();
        if (restrictions.size() != command.restrictions().size()) {
            throw TransportException.invalidOffer();
        }
        return new PublishCapacityOffer(
                command.requestId(),
                command.vehicleId(),
                command.driverAssignmentId(),
                sequenced,
                command.corridorRadiusMetres(),
                start,
                end,
                expiry,
                restrictions,
                new Capacity(
                        positiveCapacity(command.capacity().weightKg(), TransportException.invalidOffer()),
                        positiveCapacity(command.capacity().volumeCubicMetres(), TransportException.invalidOffer())));
    }

    private static Vehicle sameVehicle(Vehicle existing, CreateVehicle command) {
        if (!existing.registrationNumber().equals(command.registrationNumber())
                || !existing.description().equals(command.description())
                || existing.maximumWeightKg().compareTo(command.maximumWeightKg()) != 0
                || existing.maximumVolumeCubicMetres().compareTo(command.maximumVolumeCubicMetres()) != 0) {
            throw TransportException.requestConflict();
        }
        return existing;
    }

    private static Driver sameDriver(Driver existing, CreateDriver command) {
        if (!existing.displayName().equals(command.displayName())
                || !existing.driverReference().equals(command.driverReference())) {
            throw TransportException.requestConflict();
        }
        return existing;
    }

    private static DriverVehicleAssignment sameAssignment(DriverVehicleAssignment existing, AssignDriver command) {
        if (!existing.vehicleId().equals(command.vehicleId())
                || !existing.driverId().equals(command.driverId())) {
            throw TransportException.requestConflict();
        }
        return existing;
    }

    private static CapacityOffer sameOffer(CapacityOffer existing, PublishCapacityOffer command) {
        boolean same = existing.vehicleId().equals(command.vehicleId())
                && existing.driverAssignmentId().equals(command.driverAssignmentId())
                && existing.routePoints().equals(command.routePoints())
                && existing.corridorRadiusMetres() == command.corridorRadiusMetres()
                && existing.departureWindowStart().equals(command.departureWindowStart())
                && existing.departureWindowEnd().equals(command.departureWindowEnd())
                && existing.expiresAt().equals(command.expiresAt())
                && existing.restrictions().equals(command.restrictions())
                && existing.totalCapacity()
                                .weightKg()
                                .compareTo(command.capacity().weightKg())
                        == 0
                && existing.totalCapacity()
                                .volumeCubicMetres()
                                .compareTo(command.capacity().volumeCubicMetres())
                        == 0;
        if (!same) {
            throw TransportException.requestConflict();
        }
        return existing;
    }

    private static BigDecimal positiveCapacity(BigDecimal value, TransportException failure) {
        if (value == null) {
            throw failure;
        }
        try {
            BigDecimal normalized = value.setScale(3, RoundingMode.UNNECESSARY);
            if (normalized.signum() <= 0 || normalized.precision() > 15) {
                throw failure;
            }
            return normalized;
        } catch (ArithmeticException tooPrecise) {
            throw failure;
        }
    }

    private static UUID requiredId(UUID value) {
        if (value == null) {
            throw TransportException.invalidAsset();
        }
        return value;
    }

    private static String requiredText(String value, int maximum) {
        if (value == null) {
            throw TransportException.invalidAsset();
        }
        String normalized = value.strip();
        if (normalized.isBlank() || normalized.length() > maximum) {
            throw TransportException.invalidAsset();
        }
        return normalized;
    }

    private static String optionalText(String value, int maximum) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > maximum) {
            throw TransportException.invalidOffer();
        }
        return normalized;
    }

    private static Instant databaseTime(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    public record CreateVehicle(
            UUID requestId,
            String registrationNumber,
            String description,
            BigDecimal maximumWeightKg,
            BigDecimal maximumVolumeCubicMetres) {}

    public record CreateDriver(UUID requestId, String displayName, String driverReference) {}

    public record AssignDriver(UUID requestId, UUID vehicleId, UUID driverId) {}

    public record PublishCapacityOffer(
            UUID requestId,
            UUID vehicleId,
            UUID driverAssignmentId,
            List<RoutePoint> routePoints,
            int corridorRadiusMetres,
            Instant departureWindowStart,
            Instant departureWindowEnd,
            Instant expiresAt,
            List<CargoRestriction> restrictions,
            Capacity capacity) {

        public PublishCapacityOffer {
            routePoints = routePoints == null ? null : List.copyOf(routePoints);
            restrictions = restrictions == null ? null : List.copyOf(restrictions);
        }
    }
}
