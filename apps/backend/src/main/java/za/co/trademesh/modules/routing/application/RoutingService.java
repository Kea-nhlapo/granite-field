package za.co.trademesh.modules.routing.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.routing.domain.CandidateRoute;
import za.co.trademesh.modules.routing.domain.GeoPoint;
import za.co.trademesh.modules.routing.domain.RouteAvoidance;
import za.co.trademesh.modules.routing.domain.RouteCalculation;
import za.co.trademesh.modules.routing.domain.RouteCalculationRepository;
import za.co.trademesh.modules.routing.domain.RouteSegment;
import za.co.trademesh.modules.routing.domain.VehicleLimits;
import za.co.trademesh.modules.routing.events.RoutingEvent;
import za.co.trademesh.shared.events.DomainEvents;

@Service
public class RoutingService {

    private static final int MAX_LABEL_LENGTH = 255;
    private static final int MAX_PROVIDER_TEXT_LENGTH = 64;
    private static final double COORDINATE_TOLERANCE = 0.000001;

    private final RouteProviderGateway providerGateway;
    private final RouteCalculationRepository calculations;
    private final RoutingProviderProperties properties;
    private final DomainEvents events;
    private final Clock clock;

    public RoutingService(
            RouteProviderGateway providerGateway,
            RouteCalculationRepository calculations,
            RoutingProviderProperties properties,
            DomainEvents events,
            Clock clock) {
        this.providerGateway = providerGateway;
        this.calculations = calculations;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
        validateProperties(properties);
    }

    @Transactional
    public RouteCalculation calculate(UUID businessId, CalculateRoutes command, UUID actorUserId) {
        UUID owner = requiredId(businessId);
        UUID actor = requiredId(actorUserId);
        CalculateRoutes normalized = normalize(command);
        if (normalized.recalculationOfId() != null) {
            calculations
                    .findById(owner, normalized.recalculationOfId())
                    .orElseThrow(RoutingException::calculationNotFound);
        }
        String fingerprint = fingerprint(normalized);
        var existing = calculations.findByRequestId(owner, normalized.requestId());
        if (existing.isPresent()) {
            if (!existing.get().inputFingerprint().equals(fingerprint)) {
                throw RoutingException.requestConflict();
            }
            return existing.get();
        }

        RouteProviderGateway.ResolvedRoutes resolved;
        try {
            resolved = providerGateway.resolve(new RouteProvider.ProviderRequest(
                    normalized.origin(),
                    normalized.destination(),
                    normalized.waypoints(),
                    normalized.vehicleLimits(),
                    normalized.avoidances(),
                    properties.maximumCandidates()));
        } catch (RouteProviderException providerFailure) {
            throw RoutingException.providerUnavailable();
        }
        RouteProvider.ProviderResult providerResult = validateProviderResult(normalized, resolved.providerResult());
        List<CandidateRoute> candidates = java.util.stream.IntStream.range(
                        0, providerResult.candidates().size())
                .mapToObj(
                        index -> mapCandidate(index, providerResult.candidates().get(index)))
                .toList();
        RouteCalculation calculation = new RouteCalculation(
                UUID.randomUUID(),
                owner,
                normalized.requestId(),
                normalized.recalculationOfId(),
                fingerprint,
                normalized.origin(),
                normalized.destination(),
                normalized.waypoints(),
                normalized.vehicleLimits(),
                normalized.avoidances(),
                providerResult.providerName(),
                providerResult.providerVersion(),
                resolved.fallbackUsed(),
                resolved.fallbackReason(),
                candidates,
                actor,
                databaseTime(clock.instant()));
        if (!calculations.save(calculation)) {
            return calculations
                    .findByRequestId(owner, normalized.requestId())
                    .filter(saved -> saved.inputFingerprint().equals(fingerprint))
                    .orElseThrow(RoutingException::requestConflict);
        }
        events.publish(
                new RoutingEvent.RouteCandidatesCalculated(
                        calculation.id(),
                        owner,
                        calculation.recalculationOfId(),
                        calculation.providerName(),
                        calculation.providerVersion(),
                        calculation.fallbackUsed(),
                        candidates.size()),
                actor.toString());
        return calculation;
    }

    @Transactional(readOnly = true)
    public RouteCalculation get(UUID businessId, UUID calculationId) {
        return calculations
                .findById(requiredId(businessId), requiredId(calculationId))
                .orElseThrow(RoutingException::calculationNotFound);
    }

    private CalculateRoutes normalize(CalculateRoutes command) {
        if (command == null
                || command.requestId() == null
                || command.origin() == null
                || command.destination() == null
                || command.waypoints() == null
                || command.vehicleLimits() == null
                || command.avoidances() == null
                || command.waypoints().size() > properties.maxWaypoints()
                || command.waypoints().stream().anyMatch(Objects::isNull)
                || command.avoidances().stream().anyMatch(Objects::isNull)) {
            throw RoutingException.invalidRequest();
        }
        GeoPoint origin = point(command.origin());
        GeoPoint destination = point(command.destination());
        if (sameCoordinates(origin, destination)) {
            throw RoutingException.invalidRequest();
        }
        List<GeoPoint> waypoints =
                command.waypoints().stream().map(RoutingService::point).toList();
        List<RouteAvoidance> avoidances = new HashSet<>(command.avoidances())
                .stream().sorted(Comparator.comparing(Enum::name)).toList();
        if (avoidances.size() != command.avoidances().size()) {
            throw RoutingException.invalidRequest();
        }
        VehicleLimits limits = new VehicleLimits(
                positive(command.vehicleLimits().maximumWeightKg(), 12),
                positive(command.vehicleLimits().maximumHeightMetres(), 5),
                positive(command.vehicleLimits().maximumWidthMetres(), 5),
                positive(command.vehicleLimits().maximumLengthMetres(), 5));
        return new CalculateRoutes(
                command.requestId(), command.recalculationOfId(), origin, destination, waypoints, limits, avoidances);
    }

    private RouteProvider.ProviderResult validateProviderResult(
            CalculateRoutes request, RouteProvider.ProviderResult result) {
        if (result == null
                || invalidText(result.providerName(), MAX_PROVIDER_TEXT_LENGTH)
                || invalidText(result.providerVersion(), MAX_PROVIDER_TEXT_LENGTH)
                || result.candidates() == null
                || result.candidates().isEmpty()
                || result.candidates().size() > properties.maximumCandidates()
                || result.candidates().stream().anyMatch(Objects::isNull)) {
            throw RoutingException.invalidProviderResult();
        }
        HashSet<String> candidateKeys = new HashSet<>();
        for (RouteProvider.ProviderCandidate candidate : result.candidates()) {
            validateCandidate(request, candidate);
            if (!candidateKeys.add(candidate.providerCandidateKey())) {
                throw RoutingException.invalidProviderResult();
            }
        }
        return new RouteProvider.ProviderResult(
                result.providerName().strip(), result.providerVersion().strip(), result.candidates());
    }

    private static void validateCandidate(CalculateRoutes request, RouteProvider.ProviderCandidate candidate) {
        if (invalidText(candidate.providerCandidateKey(), 128)
                || invalidText(candidate.label(), 100)
                || candidate.geometry() == null
                || candidate.geometry().size() < 2
                || candidate.geometry().stream().anyMatch(point -> !validCoordinates(point))
                || !sameCoordinates(candidate.geometry().getFirst(), request.origin())
                || !sameCoordinates(candidate.geometry().getLast(), request.destination())
                || candidate.distanceMetres() <= 0
                || candidate.durationSeconds() <= 0
                || invalidMoney(candidate.tollEstimateZar())
                || candidate.segments() == null
                || candidate.segments().isEmpty()
                || candidate.segments().stream().anyMatch(Objects::isNull)) {
            throw RoutingException.invalidProviderResult();
        }
        long segmentDistance = 0;
        long segmentDuration = 0;
        BigDecimal segmentToll = BigDecimal.ZERO;
        for (int index = 0; index < candidate.segments().size(); index++) {
            RouteProvider.ProviderSegment segment = candidate.segments().get(index);
            if (segment.sequence() != index
                    || segment.geometry() == null
                    || segment.geometry().size() < 2
                    || segment.geometry().stream().anyMatch(point -> !validCoordinates(point))
                    || segment.distanceMetres() <= 0
                    || segment.durationSeconds() <= 0
                    || invalidMoney(segment.tollEstimateZar())) {
                throw RoutingException.invalidProviderResult();
            }
            segmentDistance = Math.addExact(segmentDistance, segment.distanceMetres());
            segmentDuration = Math.addExact(segmentDuration, segment.durationSeconds());
            segmentToll = segmentToll.add(segment.tollEstimateZar());
        }
        if (segmentDistance != candidate.distanceMetres()
                || segmentDuration != candidate.durationSeconds()
                || segmentToll.compareTo(candidate.tollEstimateZar()) != 0) {
            throw RoutingException.invalidProviderResult();
        }
    }

    private static CandidateRoute mapCandidate(int sequence, RouteProvider.ProviderCandidate candidate) {
        List<RouteSegment> segments = candidate.segments().stream()
                .map(segment -> new RouteSegment(
                        UUID.randomUUID(),
                        segment.sequence(),
                        optionalText(segment.fromLabel(), MAX_LABEL_LENGTH),
                        optionalText(segment.toLabel(), MAX_LABEL_LENGTH),
                        segment.geometry(),
                        segment.distanceMetres(),
                        segment.durationSeconds(),
                        money(segment.tollEstimateZar())))
                .toList();
        return new CandidateRoute(
                UUID.randomUUID(),
                sequence,
                candidate.providerCandidateKey().strip(),
                candidate.label().strip(),
                candidate.geometry(),
                candidate.distanceMetres(),
                candidate.durationSeconds(),
                money(candidate.tollEstimateZar()),
                segments);
    }

    private String fingerprint(CalculateRoutes command) {
        String value = command.recalculationOfId() + "|" + pointValue(command.origin()) + "|"
                + pointValue(command.destination()) + "|"
                + command.waypoints().stream().map(RoutingService::pointValue).toList() + "|"
                + command.vehicleLimits() + "|" + command.avoidances() + "|" + properties.provider() + "|"
                + properties.maximumCandidates();
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static GeoPoint point(GeoPoint point) {
        if (!validCoordinates(point)) {
            throw RoutingException.invalidRequest();
        }
        return new GeoPoint(optionalText(point.label(), MAX_LABEL_LENGTH), point.latitude(), point.longitude());
    }

    private static boolean validCoordinates(GeoPoint point) {
        return point != null
                && Double.isFinite(point.latitude())
                && point.latitude() >= -90
                && point.latitude() <= 90
                && Double.isFinite(point.longitude())
                && point.longitude() >= -180
                && point.longitude() <= 180;
    }

    private static boolean sameCoordinates(GeoPoint first, GeoPoint second) {
        return Math.abs(first.latitude() - second.latitude()) <= COORDINATE_TOLERANCE
                && Math.abs(first.longitude() - second.longitude()) <= COORDINATE_TOLERANCE;
    }

    private static BigDecimal positive(BigDecimal value, int maximumIntegerDigits) {
        if (value == null) {
            throw RoutingException.invalidRequest();
        }
        try {
            BigDecimal normalized = value.setScale(3, RoundingMode.UNNECESSARY);
            if (normalized.signum() <= 0 || normalized.precision() - normalized.scale() > maximumIntegerDigits) {
                throw RoutingException.invalidRequest();
            }
            return normalized;
        } catch (ArithmeticException tooPrecise) {
            throw RoutingException.invalidRequest();
        }
    }

    private static boolean invalidMoney(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            return true;
        }
        try {
            value.setScale(2, RoundingMode.UNNECESSARY);
            return value.precision() - value.scale() > 13;
        } catch (ArithmeticException tooPrecise) {
            return true;
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static String optionalText(String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > maximumLength) {
            throw RoutingException.invalidRequest();
        }
        return normalized;
    }

    private static boolean invalidText(String value, int maximumLength) {
        return value == null || value.isBlank() || value.strip().length() > maximumLength;
    }

    private static String pointValue(GeoPoint point) {
        return point.label() + ":" + point.latitude() + ":" + point.longitude();
    }

    private static UUID requiredId(UUID value) {
        if (value == null) {
            throw RoutingException.invalidRequest();
        }
        return value;
    }

    private static Instant databaseTime(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    private static void validateProperties(RoutingProviderProperties properties) {
        if (properties == null
                || properties.provider() == null
                || properties.provider().isBlank()
                || properties.timeout() == null
                || properties.timeout().isZero()
                || properties.timeout().isNegative()
                || properties.maxWaypoints() < 0
                || properties.maxWaypoints() > 100
                || properties.maximumCandidates() < 1
                || properties.maximumCandidates() > 10) {
            throw new IllegalStateException("Routing provider configuration is invalid");
        }
    }

    public record CalculateRoutes(
            UUID requestId,
            UUID recalculationOfId,
            GeoPoint origin,
            GeoPoint destination,
            List<GeoPoint> waypoints,
            VehicleLimits vehicleLimits,
            List<RouteAvoidance> avoidances) {

        public CalculateRoutes {
            waypoints = waypoints == null ? null : List.copyOf(waypoints);
            avoidances = avoidances == null ? null : List.copyOf(avoidances);
        }
    }
}
