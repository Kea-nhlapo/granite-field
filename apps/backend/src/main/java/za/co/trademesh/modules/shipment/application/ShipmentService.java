package za.co.trademesh.modules.shipment.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.aggregation.application.ConsolidatedDemandCatalog;
import za.co.trademesh.modules.routing.application.ScoredRouteCatalog;
import za.co.trademesh.modules.shipment.domain.Shipment;
import za.co.trademesh.modules.shipment.domain.ShipmentActionSource;
import za.co.trademesh.modules.shipment.domain.ShipmentAssignment;
import za.co.trademesh.modules.shipment.domain.ShipmentCargoItem;
import za.co.trademesh.modules.shipment.domain.ShipmentLoadOrder;
import za.co.trademesh.modules.shipment.domain.ShipmentRepository;
import za.co.trademesh.modules.shipment.domain.ShipmentRoutePoint;
import za.co.trademesh.modules.shipment.domain.ShipmentStatus;
import za.co.trademesh.modules.shipment.domain.ShipmentTransition;
import za.co.trademesh.modules.shipment.events.ShipmentEvent;
import za.co.trademesh.modules.transport.application.ReservedCapacityCatalog;
import za.co.trademesh.shared.events.DomainEvents;

@Service
public class ShipmentService {

    private static final int MAX_REASON_LENGTH = 500;

    private final ConsolidatedDemandCatalog demandCatalog;
    private final ReservedCapacityCatalog capacityCatalog;
    private final ScoredRouteCatalog routeCatalog;
    private final ShipmentRepository shipments;
    private final DomainEvents events;
    private final Clock clock;

    public ShipmentService(
            ConsolidatedDemandCatalog demandCatalog,
            ReservedCapacityCatalog capacityCatalog,
            ScoredRouteCatalog routeCatalog,
            ShipmentRepository shipments,
            DomainEvents events,
            Clock clock) {
        this.demandCatalog = demandCatalog;
        this.capacityCatalog = capacityCatalog;
        this.routeCatalog = routeCatalog;
        this.shipments = shipments;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Shipment create(UUID businessId, CreateShipment command, UUID actorUserId, ShipmentActionSource source) {
        UUID owner = requiredId(businessId);
        UUID actor = requiredId(actorUserId);
        CreateShipment normalized = normalize(command);
        ShipmentActionSource actionSource = requiredSource(source);
        String fingerprint = fingerprint(
                "CREATE",
                normalized.demandGroupSuggestionId(),
                normalized.capacitySearchId(),
                normalized.capacityReservationId(),
                normalized.routeAssessmentId(),
                normalized.routeCandidateId(),
                normalized.reason(),
                normalized.correlationId(),
                actionSource);
        var existing = shipments.findByRequestId(owner, normalized.requestId());
        if (existing.isPresent()) {
            if (!existing.get().inputFingerprint().equals(fingerprint)) {
                throw ShipmentException.requestConflict();
            }
            return existing.get();
        }

        var demand = demandCatalog
                .findActive(owner, normalized.demandGroupSuggestionId())
                .orElseThrow(ShipmentException::prerequisitesNotReady);
        var claimedCapacity = capacityCatalog
                .claimReserved(owner, normalized.capacitySearchId(), normalized.capacityReservationId())
                .filter(value -> value.demandGroupSuggestionId().equals(demand.suggestionId()));
        if (claimedCapacity.isEmpty()) {
            var racedRequest = shipments.findByRequestId(owner, normalized.requestId());
            if (racedRequest.isPresent()) {
                if (!racedRequest.get().inputFingerprint().equals(fingerprint)) {
                    throw ShipmentException.requestConflict();
                }
                return racedRequest.get();
            }
            throw ShipmentException.prerequisitesNotReady();
        }
        var capacity = claimedCapacity.get();
        var route = routeCatalog
                .findScoredRoute(owner, normalized.routeAssessmentId(), normalized.routeCandidateId())
                .orElseThrow(ShipmentException::prerequisitesNotReady);
        Instant now = databaseTime(clock.instant());
        List<ShipmentLoadOrder> loadOrders = java.util.stream.IntStream.range(
                        0, demand.deliveryStops().size())
                .mapToObj(index -> loadOrder(index, demand.deliveryStops().get(index)))
                .toList();
        ShipmentAssignment assignment = assignment(
                UUID.randomUUID(),
                normalized.requestId(),
                0,
                capacity.assignment(),
                route,
                normalized.reason(),
                normalized.correlationId(),
                actionSource,
                actor,
                now);
        ShipmentTransition initialTransition = new ShipmentTransition(
                UUID.randomUUID(),
                normalized.requestId(),
                fingerprint(
                        "TRANSITION",
                        ShipmentStatus.AWAITING_COLLECTION,
                        normalized.reason(),
                        normalized.correlationId(),
                        actionSource),
                null,
                ShipmentStatus.AWAITING_COLLECTION,
                actor,
                now,
                normalized.reason(),
                normalized.correlationId(),
                actionSource);
        Shipment shipment = new Shipment(
                UUID.randomUUID(),
                owner,
                normalized.requestId(),
                fingerprint,
                demand.suggestionId(),
                capacity.searchId(),
                capacity.reservationId(),
                capacity.offerId(),
                capacity.transporterId(),
                capacity.reservedWeightKg(),
                capacity.reservedVolumeCubicMetres(),
                ShipmentStatus.AWAITING_COLLECTION,
                loadOrders,
                List.of(assignment),
                List.of(initialTransition),
                actor,
                now,
                now);
        if (!shipments.save(shipment)) {
            return shipments
                    .findByRequestId(owner, normalized.requestId())
                    .filter(saved -> saved.inputFingerprint().equals(fingerprint))
                    .orElseThrow(ShipmentException::requestConflict);
        }
        events.publish(
                new ShipmentEvent.ShipmentCreated(
                        shipment.id(),
                        owner,
                        shipment.demandGroupSuggestionId(),
                        shipment.capacityReservationId(),
                        assignment.routeCandidateId()),
                actor.toString());
        return shipment;
    }

    @Transactional(readOnly = true)
    public Shipment get(UUID businessId, UUID shipmentId) {
        return shipments
                .findById(requiredId(businessId), requiredId(shipmentId))
                .orElseThrow(ShipmentException::notFound);
    }

    @Transactional
    public Shipment transition(
            UUID businessId,
            UUID shipmentId,
            TransitionShipment command,
            UUID actorUserId,
            ShipmentActionSource source) {
        UUID owner = requiredId(businessId);
        UUID actor = requiredId(actorUserId);
        TransitionShipment normalized = normalize(command);
        ShipmentActionSource actionSource = requiredSource(source);
        Shipment current =
                shipments.findByIdForUpdate(owner, requiredId(shipmentId)).orElseThrow(ShipmentException::notFound);
        String fingerprint = fingerprint(
                "TRANSITION", normalized.targetStatus(), normalized.reason(), normalized.correlationId(), actionSource);
        var existing = shipments.findTransitionByCommandId(current.id(), normalized.commandId());
        if (existing.isPresent()) {
            if (!existing.get().inputFingerprint().equals(fingerprint)) {
                throw ShipmentException.transitionConflict();
            }
            return current;
        }
        if (!current.status().canTransitionTo(normalized.targetStatus())) {
            throw ShipmentException.invalidTransition();
        }
        ShipmentTransition transition = new ShipmentTransition(
                UUID.randomUUID(),
                normalized.commandId(),
                fingerprint,
                current.status(),
                normalized.targetStatus(),
                actor,
                databaseTime(clock.instant()),
                normalized.reason(),
                normalized.correlationId(),
                actionSource);
        if (!shipments.addTransition(current.id(), current.status(), transition)) {
            throw ShipmentException.transitionConflict();
        }
        events.publish(
                new ShipmentEvent.ShipmentStatusChanged(
                        current.id(),
                        current.status(),
                        normalized.targetStatus(),
                        normalized.correlationId(),
                        actionSource),
                actor.toString());
        return shipments.findById(owner, current.id()).orElseThrow(ShipmentException::notFound);
    }

    @Transactional
    public Shipment changeAssignment(
            UUID businessId, UUID shipmentId, ChangeAssignment command, UUID actorUserId, ShipmentActionSource source) {
        UUID owner = requiredId(businessId);
        UUID actor = requiredId(actorUserId);
        ChangeAssignment normalized = normalize(command);
        ShipmentActionSource actionSource = requiredSource(source);
        Shipment current =
                shipments.findByIdForUpdate(owner, requiredId(shipmentId)).orElseThrow(ShipmentException::notFound);
        String fingerprint = fingerprint(
                "ASSIGNMENT",
                normalized.transportAssignmentId(),
                normalized.routeAssessmentId(),
                normalized.routeCandidateId(),
                normalized.reason(),
                normalized.correlationId(),
                actionSource);
        var existing = shipments.findAssignmentByCommandId(current.id(), normalized.commandId());
        if (existing.isPresent()) {
            if (!existing.get().inputFingerprint().equals(fingerprint)) {
                throw ShipmentException.assignmentConflict();
            }
            return current;
        }
        if (current.status() == ShipmentStatus.DELIVERED
                || current.status() == ShipmentStatus.DISPUTED
                || current.status() == ShipmentStatus.CANCELLED) {
            throw ShipmentException.assignmentConflict();
        }
        var transport = capacityCatalog
                .findActiveAssignment(current.transporterId(), normalized.transportAssignmentId())
                .orElseThrow(ShipmentException::assignmentConflict);
        if (transport.vehicleMaximumWeightKg().compareTo(current.reservedWeightKg()) < 0
                || transport.vehicleMaximumVolumeCubicMetres().compareTo(current.reservedVolumeCubicMetres()) < 0) {
            throw ShipmentException.assignmentConflict();
        }
        var route = routeCatalog
                .findScoredRoute(owner, normalized.routeAssessmentId(), normalized.routeCandidateId())
                .orElseThrow(ShipmentException::assignmentConflict);
        ShipmentAssignment previous = current.currentAssignment();
        if (previous.transportAssignmentId().equals(transport.assignmentId())
                && previous.routeAssessmentId().equals(route.assessmentId())
                && previous.routeCandidateId().equals(route.candidateId())) {
            throw ShipmentException.assignmentConflict();
        }
        Instant now = databaseTime(clock.instant());
        ShipmentAssignment replacement = assignment(
                UUID.randomUUID(),
                normalized.commandId(),
                current.assignments().size(),
                transport,
                route,
                normalized.reason(),
                normalized.correlationId(),
                actionSource,
                actor,
                now);
        if (!shipments.replaceAssignment(current.id(), previous.id(), replacement, now)) {
            throw ShipmentException.assignmentConflict();
        }
        events.publish(
                new ShipmentEvent.ShipmentAssignmentChanged(
                        current.id(),
                        previous.id(),
                        replacement.id(),
                        replacement.vehicleId(),
                        replacement.driverId(),
                        replacement.routeCandidateId(),
                        normalized.correlationId()),
                actor.toString());
        return shipments.findById(owner, current.id()).orElseThrow(ShipmentException::notFound);
    }

    private static ShipmentAssignment assignment(
            UUID id,
            UUID commandId,
            int sequence,
            ReservedCapacityCatalog.TransportAssignment transport,
            ScoredRouteCatalog.ScoredRoute route,
            String reason,
            UUID correlationId,
            ShipmentActionSource source,
            UUID actor,
            Instant startedAt) {
        String fingerprint = fingerprint(
                "ASSIGNMENT",
                transport.assignmentId(),
                route.assessmentId(),
                route.candidateId(),
                reason,
                correlationId,
                source);
        return new ShipmentAssignment(
                id,
                commandId,
                fingerprint,
                sequence,
                transport.transporterId(),
                transport.assignmentId(),
                transport.vehicleId(),
                transport.vehicleRegistrationNumber(),
                transport.vehicleDescription(),
                transport.driverId(),
                transport.driverDisplayName(),
                transport.driverReference(),
                route.assessmentId(),
                route.calculationId(),
                route.candidateId(),
                route.cargoProfile(),
                route.algorithmVersion(),
                route.totalScore(),
                route.confidence(),
                route.geometry().stream()
                        .map(point -> new ShipmentRoutePoint(point.latitude(), point.longitude()))
                        .toList(),
                route.distanceMetres(),
                route.durationSeconds(),
                route.tollEstimateZar(),
                startedAt,
                null,
                reason,
                correlationId,
                source,
                actor);
    }

    private static ShipmentLoadOrder loadOrder(int sequence, ConsolidatedDemandCatalog.DeliveryStop stop) {
        return new ShipmentLoadOrder(
                sequence,
                stop.orderId(),
                stop.buyerBusinessId(),
                requiredText(stop.destinationLabel()),
                stop.latitude(),
                stop.longitude(),
                stop.deliveryWindowStart(),
                stop.deliveryWindowEnd(),
                stop.cargoItems().stream()
                        .map(item -> new ShipmentCargoItem(
                                requiredText(item.productCode()), requiredText(item.unitOfMeasure())))
                        .toList());
    }

    private static CreateShipment normalize(CreateShipment command) {
        if (command == null) {
            throw ShipmentException.invalidRequest();
        }
        return new CreateShipment(
                requiredId(command.requestId()),
                requiredId(command.demandGroupSuggestionId()),
                requiredId(command.capacitySearchId()),
                requiredId(command.capacityReservationId()),
                requiredId(command.routeAssessmentId()),
                requiredId(command.routeCandidateId()),
                reason(command.reason()),
                requiredId(command.correlationId()));
    }

    private static TransitionShipment normalize(TransitionShipment command) {
        if (command == null || command.targetStatus() == null) {
            throw ShipmentException.invalidRequest();
        }
        return new TransitionShipment(
                requiredId(command.commandId()),
                command.targetStatus(),
                reason(command.reason()),
                requiredId(command.correlationId()));
    }

    private static ChangeAssignment normalize(ChangeAssignment command) {
        if (command == null) {
            throw ShipmentException.invalidRequest();
        }
        return new ChangeAssignment(
                requiredId(command.commandId()),
                requiredId(command.transportAssignmentId()),
                requiredId(command.routeAssessmentId()),
                requiredId(command.routeCandidateId()),
                reason(command.reason()),
                requiredId(command.correlationId()));
    }

    private static String reason(String value) {
        String normalized = requiredText(value);
        if (normalized.length() > MAX_REASON_LENGTH) {
            throw ShipmentException.invalidRequest();
        }
        return normalized;
    }

    private static String requiredText(String value) {
        if (value == null || value.isBlank()) {
            throw ShipmentException.invalidRequest();
        }
        return value.strip();
    }

    private static UUID requiredId(UUID value) {
        if (value == null) {
            throw ShipmentException.invalidRequest();
        }
        return value;
    }

    private static ShipmentActionSource requiredSource(ShipmentActionSource value) {
        if (value == null) {
            throw ShipmentException.invalidRequest();
        }
        return value;
    }

    private static String fingerprint(Object... values) {
        String value =
                java.util.Arrays.stream(values).map(String::valueOf).collect(java.util.stream.Collectors.joining("|"));
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static Instant databaseTime(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    public record CreateShipment(
            UUID requestId,
            UUID demandGroupSuggestionId,
            UUID capacitySearchId,
            UUID capacityReservationId,
            UUID routeAssessmentId,
            UUID routeCandidateId,
            String reason,
            UUID correlationId) {}

    public record TransitionShipment(UUID commandId, ShipmentStatus targetStatus, String reason, UUID correlationId) {}

    public record ChangeAssignment(
            UUID commandId,
            UUID transportAssignmentId,
            UUID routeAssessmentId,
            UUID routeCandidateId,
            String reason,
            UUID correlationId) {}
}
