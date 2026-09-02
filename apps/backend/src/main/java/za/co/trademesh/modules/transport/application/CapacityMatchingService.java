package za.co.trademesh.modules.transport.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.aggregation.application.ConsolidatedDemandCatalog;
import za.co.trademesh.modules.transport.domain.Capacity;
import za.co.trademesh.modules.transport.domain.CapacityConstraintOutcome;
import za.co.trademesh.modules.transport.domain.CapacityConstraintResult;
import za.co.trademesh.modules.transport.domain.CapacityMatchCandidate;
import za.co.trademesh.modules.transport.domain.CapacityMatchConstraint;
import za.co.trademesh.modules.transport.domain.CapacityMatchSearch;
import za.co.trademesh.modules.transport.domain.CapacityMatchStatus;
import za.co.trademesh.modules.transport.domain.CapacityMatchingRepository;
import za.co.trademesh.modules.transport.domain.CapacityOffer;
import za.co.trademesh.modules.transport.domain.CapacityReservation;
import za.co.trademesh.modules.transport.domain.CapacityReservationStatus;
import za.co.trademesh.modules.transport.domain.CapacityScoreComponent;
import za.co.trademesh.modules.transport.domain.CargoRestriction;
import za.co.trademesh.modules.transport.domain.CargoTrait;
import za.co.trademesh.modules.transport.domain.OfferRouteFit;
import za.co.trademesh.modules.transport.domain.RoutePoint;
import za.co.trademesh.modules.transport.domain.TransportRepository;
import za.co.trademesh.modules.transport.events.TransportEvent;
import za.co.trademesh.shared.events.DomainEvents;

@Service
public class CapacityMatchingService {

    private static final String DISTANCE_COMPONENT = "ADDED_DISTANCE";
    private static final String CAPACITY_COMPONENT = "CAPACITY_FIT";
    private static final String COST_COMPONENT = "ESTIMATED_COST";
    private static final String TIMING_COMPONENT = "TIMING_OVERLAP";

    private final ConsolidatedDemandCatalog demandCatalog;
    private final TransportRepository transport;
    private final CapacityMatchingRepository matches;
    private final CapacityOfferInventory inventory;
    private final CapacityMatchingProperties properties;
    private final DomainEvents events;
    private final Clock clock;

    public CapacityMatchingService(
            ConsolidatedDemandCatalog demandCatalog,
            TransportRepository transport,
            CapacityMatchingRepository matches,
            CapacityOfferInventory inventory,
            CapacityMatchingProperties properties,
            DomainEvents events,
            Clock clock) {
        this.demandCatalog = demandCatalog;
        this.transport = transport;
        this.matches = matches;
        this.inventory = inventory;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
        validateProperties(properties);
    }

    @Transactional
    public CapacityMatchSearch search(UUID businessId, SearchCapacity command, UUID actorUserId) {
        UUID owner = requiredId(businessId);
        UUID actor = requiredId(actorUserId);
        SearchCapacity normalized = normalize(command);
        var demand = demandCatalog
                .findActive(owner, normalized.demandGroupSuggestionId())
                .orElseThrow(CapacityMatchingException::demandNotFound);
        Instant windowStart = demand.deliveryStops().stream()
                .map(ConsolidatedDemandCatalog.DeliveryStop::deliveryWindowStart)
                .max(Comparator.naturalOrder())
                .orElseThrow(CapacityMatchingException::demandNotFound);
        Instant windowEnd = demand.deliveryStops().stream()
                .map(ConsolidatedDemandCatalog.DeliveryStop::deliveryWindowEnd)
                .min(Comparator.naturalOrder())
                .orElseThrow(CapacityMatchingException::demandNotFound);
        if (!windowEnd.isAfter(windowStart)) {
            throw CapacityMatchingException.demandWindowConflict();
        }

        String fingerprint = fingerprint(normalized, windowStart, windowEnd, demand.deliveryStops());
        var existing = matches.findSearchByRequestId(owner, normalized.requestId());
        if (existing.isPresent()) {
            if (!existing.get().inputFingerprint().equals(fingerprint)) {
                throw CapacityMatchingException.requestConflict();
            }
            return existing.get();
        }

        List<RoutePoint> destinations = java.util.stream.IntStream.range(
                        0, demand.deliveryStops().size())
                .mapToObj(index -> {
                    var stop = demand.deliveryStops().get(index);
                    return new RoutePoint(index, stop.destinationLabel(), stop.latitude(), stop.longitude());
                })
                .toList();
        List<CapacityMatchCandidate> evaluated =
                transport.findAvailableOffers(databaseTime(clock.instant()), properties.candidateLimit()).stream()
                        .map(offer -> evaluate(offer, normalized, windowStart, windowEnd, destinations))
                        .toList();
        List<CapacityMatchCandidate> ranked = rank(evaluated);
        CapacityMatchStatus status = ranked.stream().anyMatch(CapacityMatchCandidate::compatible)
                ? CapacityMatchStatus.MATCHED
                : CapacityMatchStatus.NO_MATCH;
        CapacityMatchSearch search = new CapacityMatchSearch(
                UUID.randomUUID(),
                owner,
                normalized.requestId(),
                normalized.demandGroupSuggestionId(),
                fingerprint,
                properties.algorithmVersion(),
                normalized.requiredCapacity(),
                normalized.cargoTraits(),
                windowStart,
                windowEnd,
                demand.deliveryStops().size(),
                status,
                ranked,
                actor,
                databaseTime(clock.instant()));
        if (!matches.saveSearch(search)) {
            return matches.findSearchByRequestId(owner, normalized.requestId())
                    .filter(saved -> saved.inputFingerprint().equals(fingerprint))
                    .orElseThrow(CapacityMatchingException::requestConflict);
        }
        events.publish(
                new TransportEvent.CapacityMatchCompleted(
                        search.id(), owner, search.demandGroupSuggestionId(), compatibleCount(ranked)),
                actor.toString());
        return search;
    }

    @Transactional(readOnly = true)
    public CapacityMatchSearch get(UUID businessId, UUID searchId) {
        return matches.findSearch(requiredId(businessId), requiredId(searchId))
                .orElseThrow(CapacityMatchingException::searchNotFound);
    }

    @Transactional
    public CapacityReservation reserve(UUID businessId, UUID searchId, ReserveCapacity command, UUID actorUserId) {
        UUID owner = requiredId(businessId);
        UUID actor = requiredId(actorUserId);
        if (command == null || command.requestId() == null || command.offerId() == null) {
            throw CapacityMatchingException.invalidRequest();
        }
        CapacityMatchSearch search = matches.findSearchForUpdate(owner, requiredId(searchId))
                .orElseThrow(CapacityMatchingException::searchNotFound);
        var existing = matches.findReservation(search.id());
        if (existing.isPresent()) {
            CapacityReservation reservation = existing.get();
            if (reservation.clientRequestId().equals(command.requestId())
                    && reservation.offerId().equals(command.offerId())) {
                return reservation;
            }
            throw CapacityMatchingException.reservationConflict();
        }
        if (search.status() != CapacityMatchStatus.MATCHED) {
            throw CapacityMatchingException.reservationConflict();
        }
        CapacityMatchCandidate candidate = matches.findCandidate(search.id(), command.offerId())
                .filter(CapacityMatchCandidate::compatible)
                .orElseThrow(CapacityMatchingException::candidateNotReservable);
        if (!inventory.tryReserve(
                candidate.offerId(),
                search.requiredCapacity().weightKg(),
                search.requiredCapacity().volumeCubicMetres())) {
            throw CapacityMatchingException.candidateNotReservable();
        }
        Instant now = databaseTime(clock.instant());
        CapacityReservation reservation = new CapacityReservation(
                UUID.randomUUID(),
                search.id(),
                command.requestId(),
                candidate.offerId(),
                search.requiredCapacity(),
                CapacityReservationStatus.ACTIVE,
                databaseTime(now.plus(properties.reservationTtl())),
                actor,
                now,
                null);
        if (!matches.saveReservation(reservation)
                || !matches.markSearchStatus(search.id(), CapacityMatchStatus.MATCHED, CapacityMatchStatus.RESERVED)) {
            throw CapacityMatchingException.reservationConflict();
        }
        events.publish(
                new TransportEvent.CapacityReserved(
                        reservation.id(), search.id(), candidate.offerId(), reservation.expiresAt()),
                actor.toString());
        return reservation;
    }

    @Transactional
    public CapacityReservation release(UUID businessId, UUID searchId, UUID actorUserId) {
        UUID owner = requiredId(businessId);
        UUID actor = requiredId(actorUserId);
        CapacityMatchSearch search = matches.findSearchForUpdate(owner, requiredId(searchId))
                .orElseThrow(CapacityMatchingException::searchNotFound);
        CapacityReservation current =
                matches.findReservation(search.id()).orElseThrow(CapacityMatchingException::reservationConflict);
        if (current.status() != CapacityReservationStatus.ACTIVE) {
            return current;
        }
        CapacityReservation updated = releaseLocked(search, current, CapacityReservationStatus.RELEASED, actor);
        events.publish(
                new TransportEvent.CapacityReleased(updated.id(), search.id(), updated.offerId(), false),
                actor.toString());
        return updated;
    }

    @Transactional
    public int expireDueReservations() {
        Instant now = databaseTime(clock.instant());
        int expired = 0;
        for (UUID reservationId : matches.findExpiredActiveReservationIds(now, properties.expiryBatchSize())) {
            var locked = matches.findReservationForUpdate(reservationId);
            if (locked.isEmpty()
                    || locked.get().status() != CapacityReservationStatus.ACTIVE
                    || locked.get().expiresAt().isAfter(now)) {
                continue;
            }
            CapacityReservation reservation = locked.get();
            CapacityMatchSearch search = matches.findSearchForUpdateById(reservation.matchSearchId())
                    .orElseThrow(CapacityMatchingException::searchNotFound);
            CapacityReservation updated = releaseLocked(
                    search, reservation, CapacityReservationStatus.EXPIRED, reservation.createdByUserId());
            events.publish(
                    new TransportEvent.CapacityReleased(updated.id(), search.id(), updated.offerId(), true),
                    "capacity-reservation-expiry");
            expired++;
        }
        return expired;
    }

    private CapacityReservation releaseLocked(
            CapacityMatchSearch search,
            CapacityReservation reservation,
            CapacityReservationStatus terminalStatus,
            UUID actorUserId) {
        if (!inventory.release(
                reservation.offerId(),
                reservation.reservedCapacity().weightKg(),
                reservation.reservedCapacity().volumeCubicMetres())) {
            throw CapacityMatchingException.releaseConflict();
        }
        Instant releasedAt = databaseTime(clock.instant());
        if (!matches.markReservationTerminal(reservation.id(), terminalStatus, releasedAt)
                || !matches.markSearchStatus(
                        search.id(),
                        CapacityMatchStatus.RESERVED,
                        terminalStatus == CapacityReservationStatus.EXPIRED
                                ? CapacityMatchStatus.EXPIRED
                                : CapacityMatchStatus.RELEASED)) {
            throw CapacityMatchingException.releaseConflict();
        }
        return new CapacityReservation(
                reservation.id(),
                reservation.matchSearchId(),
                reservation.clientRequestId(),
                reservation.offerId(),
                reservation.reservedCapacity(),
                terminalStatus,
                reservation.expiresAt(),
                reservation.createdByUserId(),
                reservation.createdAt(),
                releasedAt);
    }

    private CapacityMatchCandidate evaluate(
            CapacityOffer offer,
            SearchCapacity command,
            Instant deliveryStart,
            Instant deliveryEnd,
            List<RoutePoint> destinations) {
        Capacity available = offer.remainingCapacity();
        long overlapSeconds = Math.max(
                0,
                Duration.between(
                                later(offer.departureWindowStart(), deliveryStart),
                                earlier(offer.departureWindowEnd(), deliveryEnd))
                        .getSeconds());
        OfferRouteFit routeFit = transport
                .measureRouteFit(offer.id(), destinations)
                .orElse(new OfferRouteFit(Double.MAX_VALUE, properties.maximumAddedDistanceMetres()));
        double addedDistance = Math.max(0, routeFit.estimatedAddedDistanceMetres());
        BigDecimal cost = BigDecimal.valueOf(addedDistance)
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP)
                .multiply(properties.estimatedCostPerKilometreZar())
                .setScale(2, RoundingMode.HALF_UP);
        List<CapacityConstraintResult> checks = List.of(
                check(
                        CapacityMatchConstraint.WEIGHT_CAPACITY,
                        available
                                        .weightKg()
                                        .compareTo(command.requiredCapacity().weightKg())
                                >= 0,
                        "Offer has " + available.weightKg() + " kg available; load requires "
                                + command.requiredCapacity().weightKg() + " kg."),
                check(
                        CapacityMatchConstraint.VOLUME_CAPACITY,
                        available
                                        .volumeCubicMetres()
                                        .compareTo(command.requiredCapacity().volumeCubicMetres())
                                >= 0,
                        "Offer has " + available.volumeCubicMetres() + " m3 available; load requires "
                                + command.requiredCapacity().volumeCubicMetres() + " m3."),
                check(
                        CapacityMatchConstraint.CARGO_RESTRICTIONS,
                        cargoAllowed(offer.restrictions(), command.cargoTraits()),
                        cargoExplanation(offer.restrictions(), command.cargoTraits())),
                check(
                        CapacityMatchConstraint.DELIVERY_WINDOW,
                        overlapSeconds > 0,
                        overlapSeconds > 0
                                ? "Departure and delivery windows overlap by " + overlapSeconds + " seconds."
                                : "Departure and delivery windows do not overlap."),
                check(
                        CapacityMatchConstraint.ROUTE_CORRIDOR,
                        routeFit.maximumDistanceMetres() <= offer.corridorRadiusMetres(),
                        "Furthest stop is " + Math.round(routeFit.maximumDistanceMetres())
                                + " m from the route; offer allows " + offer.corridorRadiusMetres() + " m."));
        boolean compatible = checks.stream().allMatch(CapacityConstraintResult::passed);
        List<CapacityScoreComponent> components = compatible
                ? components(
                        available,
                        command.requiredCapacity(),
                        addedDistance,
                        cost,
                        overlapSeconds,
                        deliveryStart,
                        deliveryEnd)
                : List.of();
        double score = compatible
                ? clamp(components.stream()
                        .mapToDouble(CapacityScoreComponent::contribution)
                        .sum())
                : 0;
        return new CapacityMatchCandidate(
                offer.id(),
                offer.transporterId(),
                compatible,
                null,
                available,
                addedDistance,
                overlapSeconds,
                cost,
                score,
                checks,
                components);
    }

    private List<CapacityScoreComponent> components(
            Capacity available,
            Capacity required,
            double addedDistance,
            BigDecimal cost,
            long overlapSeconds,
            Instant deliveryStart,
            Instant deliveryEnd) {
        double distanceValue = 1 - clamp(addedDistance / properties.maximumAddedDistanceMetres());
        double weightFit = ratio(required.weightKg(), available.weightKg());
        double volumeFit = ratio(required.volumeCubicMetres(), available.volumeCubicMetres());
        double capacityValue = clamp((weightFit + volumeFit) / 2);
        double maximumCost = properties.maximumAddedDistanceMetres()
                / 1000
                * properties.estimatedCostPerKilometreZar().doubleValue();
        double costValue = 1 - clamp(cost.doubleValue() / maximumCost);
        long deliveryWindowSeconds =
                Math.max(1, Duration.between(deliveryStart, deliveryEnd).getSeconds());
        double timingValue = clamp((double) overlapSeconds / deliveryWindowSeconds);
        return List.of(
                component(
                        DISTANCE_COMPONENT,
                        addedDistance,
                        distanceValue,
                        properties.distanceWeight(),
                        "Less added distance ranks higher."),
                component(
                        CAPACITY_COMPONENT,
                        Math.min(weightFit, volumeFit),
                        capacityValue,
                        properties.capacityFitWeight(),
                        "Offers that fit the load without excessive unused space rank higher."),
                component(
                        COST_COMPONENT,
                        cost.doubleValue(),
                        costValue,
                        properties.costWeight(),
                        "Lower estimated detour cost ranks higher."),
                component(
                        TIMING_COMPONENT,
                        overlapSeconds,
                        timingValue,
                        properties.timingWeight(),
                        "A longer overlap with the delivery window ranks higher."));
    }

    private static List<CapacityMatchCandidate> rank(List<CapacityMatchCandidate> evaluated) {
        List<CapacityMatchCandidate> compatible = evaluated.stream()
                .filter(CapacityMatchCandidate::compatible)
                .sorted(Comparator.comparingDouble(CapacityMatchCandidate::score)
                        .reversed()
                        .thenComparingDouble(CapacityMatchCandidate::addedDistanceMetres)
                        .thenComparing(CapacityMatchCandidate::offerId))
                .toList();
        java.util.Map<UUID, Integer> ranks = new java.util.HashMap<>();
        for (int index = 0; index < compatible.size(); index++) {
            ranks.put(compatible.get(index).offerId(), index + 1);
        }
        return evaluated.stream()
                .sorted(Comparator.<CapacityMatchCandidate, Boolean>comparing(CapacityMatchCandidate::compatible)
                        .reversed()
                        .thenComparing(candidate -> ranks.getOrDefault(candidate.offerId(), Integer.MAX_VALUE))
                        .thenComparing(CapacityMatchCandidate::offerId))
                .map(candidate -> new CapacityMatchCandidate(
                        candidate.offerId(),
                        candidate.transporterId(),
                        candidate.compatible(),
                        ranks.get(candidate.offerId()),
                        candidate.availableCapacity(),
                        candidate.addedDistanceMetres(),
                        candidate.timingOverlapSeconds(),
                        candidate.estimatedCostZar(),
                        candidate.score(),
                        candidate.constraintResults(),
                        candidate.scoreComponents()))
                .toList();
    }

    private static SearchCapacity normalize(SearchCapacity command) {
        if (command == null
                || command.requestId() == null
                || command.demandGroupSuggestionId() == null
                || command.requiredCapacity() == null
                || command.cargoTraits() == null
                || command.cargoTraits().isEmpty()
                || command.cargoTraits().stream().anyMatch(Objects::isNull)) {
            throw CapacityMatchingException.invalidRequest();
        }
        EnumSet<CargoTrait> unique = EnumSet.copyOf(command.cargoTraits());
        if (unique.size() != command.cargoTraits().size()) {
            throw CapacityMatchingException.invalidRequest();
        }
        return new SearchCapacity(
                command.requestId(),
                command.demandGroupSuggestionId(),
                new Capacity(
                        positiveCapacity(command.requiredCapacity().weightKg()),
                        positiveCapacity(command.requiredCapacity().volumeCubicMetres())),
                unique.stream().sorted(Comparator.comparing(Enum::name)).toList());
    }

    private String fingerprint(
            SearchCapacity command,
            Instant windowStart,
            Instant windowEnd,
            List<ConsolidatedDemandCatalog.DeliveryStop> stops) {
        StringBuilder value = new StringBuilder()
                .append(properties.algorithmVersion())
                .append('|')
                .append(command.demandGroupSuggestionId())
                .append('|')
                .append(command.requiredCapacity().weightKg().toPlainString())
                .append('|')
                .append(command.requiredCapacity().volumeCubicMetres().toPlainString())
                .append('|')
                .append(command.cargoTraits())
                .append('|')
                .append(windowStart)
                .append('|')
                .append(windowEnd);
        stops.stream()
                .sorted(Comparator.comparing(ConsolidatedDemandCatalog.DeliveryStop::orderId))
                .forEach(stop -> value.append('|')
                        .append(stop.orderId())
                        .append(':')
                        .append(stop.latitude())
                        .append(':')
                        .append(stop.longitude()));
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static CapacityConstraintResult check(
            CapacityMatchConstraint constraint, boolean passed, String explanation) {
        return new CapacityConstraintResult(
                constraint, passed ? CapacityConstraintOutcome.PASS : CapacityConstraintOutcome.FAIL, explanation);
    }

    private static CapacityScoreComponent component(
            String code, double raw, double normalized, double weight, String explanation) {
        return new CapacityScoreComponent(code, raw, normalized, weight, clamp(normalized * weight), explanation);
    }

    private static boolean cargoAllowed(List<CargoRestriction> restrictions, List<CargoTrait> traits) {
        return (!restrictions.contains(CargoRestriction.NO_HAZARDOUS_GOODS)
                        || !traits.contains(CargoTrait.HAZARDOUS_GOODS))
                && (!restrictions.contains(CargoRestriction.NO_HIGH_VALUE_CARGO)
                        || !traits.contains(CargoTrait.HIGH_VALUE))
                && (!restrictions.contains(CargoRestriction.NO_TEMPERATURE_CONTROLLED_CARGO)
                        || !traits.contains(CargoTrait.TEMPERATURE_CONTROLLED))
                && (!restrictions.contains(CargoRestriction.FOOD_GRADE_ONLY) || traits.contains(CargoTrait.FOOD_GRADE))
                && (!restrictions.contains(CargoRestriction.DRY_GOODS_ONLY) || traits.contains(CargoTrait.DRY_GOODS));
    }

    private static String cargoExplanation(List<CargoRestriction> restrictions, List<CargoTrait> traits) {
        return cargoAllowed(restrictions, traits)
                ? "Cargo traits satisfy all published offer restrictions."
                : "Cargo traits conflict with one or more published offer restrictions.";
    }

    private static BigDecimal positiveCapacity(BigDecimal value) {
        if (value == null) {
            throw CapacityMatchingException.invalidRequest();
        }
        try {
            BigDecimal normalized = value.setScale(3, RoundingMode.UNNECESSARY);
            if (normalized.signum() <= 0 || normalized.precision() > 15) {
                throw CapacityMatchingException.invalidRequest();
            }
            return normalized;
        } catch (ArithmeticException tooPrecise) {
            throw CapacityMatchingException.invalidRequest();
        }
    }

    private static UUID requiredId(UUID value) {
        if (value == null) {
            throw CapacityMatchingException.invalidRequest();
        }
        return value;
    }

    private static double ratio(BigDecimal required, BigDecimal available) {
        return clamp(required.divide(available, 9, RoundingMode.HALF_UP).doubleValue());
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static Instant earlier(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private static Instant later(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }

    private static Instant databaseTime(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    private static int compatibleCount(List<CapacityMatchCandidate> candidates) {
        return (int)
                candidates.stream().filter(CapacityMatchCandidate::compatible).count();
    }

    private static void validateProperties(CapacityMatchingProperties value) {
        double weightTotal =
                value.distanceWeight() + value.capacityFitWeight() + value.costWeight() + value.timingWeight();
        if (value.candidateLimit() < 1
                || value.expiryBatchSize() < 1
                || value.maximumAddedDistanceMetres() <= 0
                || value.estimatedCostPerKilometreZar() == null
                || value.estimatedCostPerKilometreZar().signum() <= 0
                || value.reservationTtl() == null
                || value.reservationTtl().isNegative()
                || value.reservationTtl().isZero()
                || value.algorithmVersion() == null
                || value.algorithmVersion().isBlank()
                || Math.abs(weightTotal - 1) > 0.000001
                || List.of(value.distanceWeight(), value.capacityFitWeight(), value.costWeight(), value.timingWeight())
                        .stream()
                        .anyMatch(weight -> weight < 0 || weight > 1)) {
            throw new IllegalStateException("Capacity matching configuration is invalid");
        }
    }

    public record SearchCapacity(
            UUID requestId, UUID demandGroupSuggestionId, Capacity requiredCapacity, List<CargoTrait> cargoTraits) {

        public SearchCapacity {
            cargoTraits = cargoTraits == null ? null : List.copyOf(cargoTraits);
        }
    }

    public record ReserveCapacity(UUID requestId, UUID offerId) {}
}
