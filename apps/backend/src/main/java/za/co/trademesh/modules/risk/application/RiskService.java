package za.co.trademesh.modules.risk.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.risk.domain.RiskEvidenceReference;
import za.co.trademesh.modules.risk.domain.RiskIndicator;
import za.co.trademesh.modules.risk.domain.RiskIndicatorState;
import za.co.trademesh.modules.risk.domain.RiskIndicatorTransition;
import za.co.trademesh.modules.risk.domain.RiskRepository;
import za.co.trademesh.modules.risk.domain.RiskRule;
import za.co.trademesh.modules.risk.domain.RiskSeverity;
import za.co.trademesh.modules.risk.events.RiskEvent;
import za.co.trademesh.modules.shipment.application.ShipmentRiskCatalog;
import za.co.trademesh.modules.shipment.application.ShipmentRiskCatalog.RiskPhase;
import za.co.trademesh.modules.shipment.application.ShipmentRiskCatalog.RoutePoint;
import za.co.trademesh.modules.shipment.application.ShipmentRiskCatalog.ShipmentRiskSnapshot;
import za.co.trademesh.modules.telemetry.application.TelemetryRiskCatalog;
import za.co.trademesh.modules.telemetry.application.TelemetryRiskCatalog.RiskReading;
import za.co.trademesh.shared.events.DomainEvents;

@Service
public class RiskService {

    private static final double EARTH_RADIUS_METRES = 6_371_000;
    private static final String READING_EVIDENCE = "TELEMETRY_READING";
    private static final String DEVICE_EVIDENCE = "TELEMETRY_DEVICE";
    private static final String SHIPMENT_EVIDENCE = "SHIPMENT";
    private static final String ASSIGNMENT_EVIDENCE = "SHIPMENT_ASSIGNMENT";

    private final RiskRepository risks;
    private final TelemetryRiskCatalog telemetry;
    private final ShipmentRiskCatalog shipments;
    private final RiskProperties properties;
    private final DomainEvents events;
    private final Clock clock;

    public RiskService(
            RiskRepository risks,
            TelemetryRiskCatalog telemetry,
            ShipmentRiskCatalog shipments,
            RiskProperties properties,
            DomainEvents events,
            Clock clock) {
        this.risks = risks;
        this.telemetry = telemetry;
        this.shipments = shipments;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void evaluateTelemetry(UUID readingId) {
        Optional<RiskReading> readingResult = telemetry.findReading(readingId);
        if (readingResult.isEmpty()) {
            return;
        }
        RiskReading current = readingResult.get();
        Optional<ShipmentRiskSnapshot> shipmentResult = shipments.find(current.shipmentId());
        if (shipmentResult.isEmpty() || shipmentResult.get().phase() == RiskPhase.TERMINAL) {
            return;
        }
        ShipmentRiskSnapshot shipment = shipmentResult.get();
        Duration windowDuration = longer(properties.unexpectedStopDuration(), properties.fuelDropWindow())
                .multipliedBy(2);
        List<RiskReading> window = telemetry.findReadingsThrough(
                current.shipmentId(),
                current.recordedAt().minus(windowDuration),
                current.recordedAt(),
                properties.readingWindowLimit());

        detectRouteDeviation(current, shipment, window);
        detectUnexpectedStop(current, shipment, window);
        detectFuelDrop(current, shipment, window);
        detectSealOpening(current, shipment);
        detectDeviceChange(current, shipment, window);
    }

    @Transactional
    public void evaluateDriverChange(
            UUID shipmentId, UUID previousAssignmentId, UUID assignmentId, UUID driverId, Instant observedAt) {
        if (previousAssignmentId == null) {
            return;
        }
        shipments
                .find(shipmentId)
                .filter(shipment -> movingOrCollected(shipment.phase()))
                .filter(shipment -> shipment.previousDriverId() != null)
                .filter(shipment -> !shipment.previousDriverId().equals(driverId))
                .ifPresent(shipment -> open(
                        shipment,
                        RiskRule.DRIVER_ASSIGNMENT_CHANGED,
                        RiskSeverity.MEDIUM,
                        "The active driver changed after collection. Operations should confirm that the change was planned.",
                        observedAt,
                        List.of(
                                evidence(ASSIGNMENT_EVIDENCE, previousAssignmentId, observedAt),
                                evidence(ASSIGNMENT_EVIDENCE, assignmentId, observedAt))));
    }

    @Transactional
    public void evaluateTimeBasedRules() {
        Instant now = databaseTime(clock.instant());
        telemetry
                .findOfflineDevices(now.minus(properties.trackerOfflineDuration()), properties.scanBatchSize())
                .forEach(device -> shipments
                        .find(device.shipmentId())
                        .filter(shipment -> moving(shipment.phase()))
                        .ifPresent(shipment -> open(
                                shipment,
                                RiskRule.TRACKER_OFFLINE,
                                RiskSeverity.HIGH,
                                "The active tracking device has not reported within the expected interval.",
                                device.lastSeenAt(),
                                List.of(evidence(DEVICE_EVIDENCE, device.deviceId(), device.lastSeenAt())))));

        shipments.findOperational(properties.scanBatchSize()).stream()
                .filter(shipment -> now.isAfter(shipment.deliveryDeadline().plus(properties.deliveryGrace())))
                .forEach(shipment -> open(
                        shipment,
                        RiskRule.DELIVERY_DELAY,
                        RiskSeverity.MEDIUM,
                        "The shipment is still open after its delivery window and configured grace period.",
                        now,
                        List.of(evidence(SHIPMENT_EVIDENCE, shipment.shipmentId(), now))));
    }

    @Transactional(readOnly = true)
    public List<RiskIndicator> listForShipment(UUID shipmentId) {
        return risks.findByShipment(requiredId(shipmentId));
    }

    @Transactional
    public RiskIndicator transition(UUID indicatorId, TransitionCommand command, UUID actorUserId) {
        UUID id = requiredId(indicatorId);
        UUID actor = requiredId(actorUserId);
        if (command == null || command.commandId() == null || command.targetState() == null) {
            throw RiskException.invalidRequest();
        }
        String note = requiredText(command.note());
        if (note.length() > 1000) {
            throw RiskException.invalidRequest();
        }
        String fingerprint = fingerprint(id + "|" + command.targetState() + "|" + note);
        Optional<RiskIndicatorTransition> prior = risks.findTransitionByCommandId(command.commandId());
        if (prior.isPresent()) {
            RiskIndicatorTransition existing = prior.get();
            if (!existing.indicatorId().equals(id)
                    || !existing.inputFingerprint().equals(fingerprint)) {
                throw RiskException.commandConflict();
            }
            return risks.findById(id).orElseThrow(RiskException::indicatorNotFound);
        }

        RiskIndicator current = risks.findByIdForUpdate(id).orElseThrow(RiskException::indicatorNotFound);
        if (!current.state().canTransitionTo(command.targetState())) {
            throw RiskException.invalidTransition();
        }
        Instant now = databaseTime(clock.instant());
        RiskIndicatorTransition transition = new RiskIndicatorTransition(
                UUID.randomUUID(),
                id,
                command.commandId(),
                fingerprint,
                current.state(),
                command.targetState(),
                actor,
                note,
                now);
        if (!risks.transition(id, current.state(), transition, now)) {
            throw RiskException.invalidTransition();
        }
        events.publish(
                new RiskEvent.IndicatorStateChanged(
                        id, current.shipmentId(), current.state(), command.targetState(), actor),
                actor.toString());
        return risks.findById(id).orElseThrow(RiskException::indicatorNotFound);
    }

    private void detectRouteDeviation(RiskReading current, ShipmentRiskSnapshot shipment, List<RiskReading> window) {
        if (!moving(shipment.phase())
                || !current.hasPosition()
                || shipment.approvedRoute().size() < 2) {
            return;
        }
        List<RiskReading> positioned = window.stream()
                .filter(RiskReading::hasPosition)
                .limit(properties.routeDeviationConfirmations())
                .toList();
        if (positioned.size() < properties.routeDeviationConfirmations()) {
            return;
        }
        List<Double> distances = positioned.stream()
                .map(reading -> distanceFromRoute(reading.latitude(), reading.longitude(), shipment.approvedRoute()))
                .toList();
        if (distances.stream().anyMatch(distance -> distance <= properties.routeDeviationMetres())) {
            return;
        }
        double nearest = distances.stream().min(Double::compareTo).orElseThrow();
        open(
                shipment,
                RiskRule.ROUTE_DEVIATION,
                RiskSeverity.HIGH,
                String.format(
                        Locale.ROOT,
                        "Consecutive positions were outside the approved route corridor; the nearest was %.0f metres away.",
                        nearest),
                current.recordedAt(),
                positioned.stream().map(RiskService::readingEvidence).toList());
    }

    private void detectUnexpectedStop(RiskReading current, ShipmentRiskSnapshot shipment, List<RiskReading> window) {
        if (!moving(shipment.phase())
                || current.speedKilometresPerHour() == null
                || current.speedKilometresPerHour().compareTo(properties.stoppedSpeedKph()) > 0) {
            return;
        }
        Instant cutoff = current.recordedAt().minus(properties.unexpectedStopDuration());
        List<RiskReading> availableSpeeds = window.stream()
                .filter(reading -> reading.speedKilometresPerHour() != null)
                .sorted(Comparator.comparing(RiskReading::recordedAt))
                .toList();
        Optional<RiskReading> baseline = availableSpeeds.stream()
                .filter(reading -> !reading.recordedAt().isAfter(cutoff))
                .reduce((first, second) -> second);
        if (baseline.isEmpty()) {
            return;
        }
        List<RiskReading> speeds = availableSpeeds.stream()
                .filter(reading -> !reading.recordedAt().isBefore(baseline.get().recordedAt()))
                .toList();
        if (speeds.size() < 2) {
            return;
        }
        if (!continuouslyStationary(speeds)) {
            return;
        }
        open(
                shipment,
                RiskRule.UNEXPECTED_STOP,
                RiskSeverity.MEDIUM,
                "The vehicle remained below the configured movement threshold for longer than expected.",
                current.recordedAt(),
                List.of(readingEvidence(speeds.getFirst()), readingEvidence(speeds.getLast())));
    }

    private void detectFuelDrop(RiskReading current, ShipmentRiskSnapshot shipment, List<RiskReading> window) {
        if (!moving(shipment.phase())
                || current.fuelLitres() == null
                || current.speedKilometresPerHour() == null
                || current.speedKilometresPerHour().compareTo(properties.stoppedSpeedKph()) > 0) {
            return;
        }
        Optional<RiskReading> baseline = window.stream()
                .filter(reading -> !reading.id().equals(current.id()))
                .filter(reading -> reading.recordedAt().isBefore(current.recordedAt()))
                .filter(reading ->
                        !reading.recordedAt().isBefore(current.recordedAt().minus(properties.fuelDropWindow())))
                .filter(reading -> reading.fuelLitres() != null && reading.speedKilometresPerHour() != null)
                .filter(reading -> reading.speedKilometresPerHour().compareTo(properties.stoppedSpeedKph()) <= 0)
                .findFirst();
        if (baseline.isEmpty()) {
            return;
        }
        RiskReading earlier = baseline.get();
        List<RiskReading> speedsBetween = window.stream()
                .filter(reading -> !reading.recordedAt().isBefore(earlier.recordedAt()))
                .filter(reading -> !reading.recordedAt().isAfter(current.recordedAt()))
                .filter(reading -> reading.speedKilometresPerHour() != null)
                .sorted(Comparator.comparing(RiskReading::recordedAt))
                .toList();
        BigDecimal drop = earlier.fuelLitres().subtract(current.fuelLitres());
        if (!continuouslyStationary(speedsBetween) || drop.compareTo(properties.fuelDropLitres()) < 0) {
            return;
        }
        open(
                shipment,
                RiskRule.STATIONARY_FUEL_DROP,
                RiskSeverity.HIGH,
                String.format(
                        Locale.ROOT,
                        "Fuel readings fell by %s litres while reported speed remained below the movement threshold.",
                        drop.stripTrailingZeros().toPlainString()),
                current.recordedAt(),
                List.of(readingEvidence(earlier), readingEvidence(current)));
    }

    private void detectSealOpening(RiskReading current, ShipmentRiskSnapshot shipment) {
        if (!movingOrCollected(shipment.phase()) || !Boolean.TRUE.equals(current.sealOpen())) {
            return;
        }
        open(
                shipment,
                RiskRule.UNEXPECTED_SEAL_OPENING,
                RiskSeverity.HIGH,
                "The cargo seal reported open after collection. This requires confirmation from operations.",
                current.recordedAt(),
                List.of(readingEvidence(current)));
    }

    private void detectDeviceChange(RiskReading current, ShipmentRiskSnapshot shipment, List<RiskReading> window) {
        if (!movingOrCollected(shipment.phase())) {
            return;
        }
        window.stream()
                .filter(reading -> !reading.id().equals(current.id()))
                .filter(reading -> reading.recordedAt().isBefore(current.recordedAt()))
                .findFirst()
                .filter(previous -> !previous.deviceId().equals(current.deviceId()))
                .ifPresent(previous -> open(
                        shipment,
                        RiskRule.TELEMETRY_DEVICE_CHANGED,
                        RiskSeverity.MEDIUM,
                        "A different telemetry device began reporting for the shipment after collection.",
                        current.recordedAt(),
                        List.of(readingEvidence(previous), readingEvidence(current))));
    }

    private RiskIndicator open(
            ShipmentRiskSnapshot shipment,
            RiskRule rule,
            RiskSeverity severity,
            String explanation,
            Instant observedAt,
            List<RiskEvidenceReference> evidence) {
        Instant now = databaseTime(clock.instant());
        UUID indicatorId = UUID.randomUUID();
        RiskIndicatorTransition initial = new RiskIndicatorTransition(
                UUID.randomUUID(),
                indicatorId,
                UUID.randomUUID(),
                fingerprint("OPEN|" + shipment.shipmentId() + "|" + rule + "|" + observedAt),
                null,
                RiskIndicatorState.OPEN,
                null,
                "Created by a deterministic operational risk rule.",
                now);
        RiskIndicator proposal = new RiskIndicator(
                indicatorId,
                shipment.shipmentId(),
                shipment.businessId(),
                rule,
                properties.ruleVersion(),
                severity,
                explanation,
                RiskIndicatorState.OPEN,
                observedAt,
                observedAt,
                now,
                now,
                evidence,
                List.of(initial));
        RiskIndicator stored = risks.upsertActive(proposal);
        if (stored.id().equals(indicatorId)) {
            events.publish(
                    new RiskEvent.IndicatorOpened(stored.id(), stored.shipmentId(), stored.rule(), stored.severity()),
                    null);
        }
        return stored;
    }

    private static RiskEvidenceReference readingEvidence(RiskReading reading) {
        return evidence(READING_EVIDENCE, reading.id(), reading.recordedAt());
    }

    private static RiskEvidenceReference evidence(String type, UUID id, Instant observedAt) {
        return new RiskEvidenceReference(type, id, observedAt);
    }

    private static boolean moving(RiskPhase phase) {
        return phase == RiskPhase.MOVING;
    }

    private static boolean movingOrCollected(RiskPhase phase) {
        return phase == RiskPhase.COLLECTED || moving(phase);
    }

    private static Duration longer(Duration left, Duration right) {
        return left.compareTo(right) >= 0 ? left : right;
    }

    private boolean continuouslyStationary(List<RiskReading> readings) {
        if (readings.stream()
                .anyMatch(reading -> reading.speedKilometresPerHour().compareTo(properties.stoppedSpeedKph()) > 0)) {
            return false;
        }
        for (int index = 1; index < readings.size(); index++) {
            Duration gap = Duration.between(
                    readings.get(index - 1).recordedAt(), readings.get(index).recordedAt());
            if (gap.compareTo(properties.maximumTelemetryGap()) > 0) {
                return false;
            }
        }
        return true;
    }

    static double distanceFromRoute(double latitude, double longitude, List<RoutePoint> route) {
        double nearest = Double.POSITIVE_INFINITY;
        for (int index = 1; index < route.size(); index++) {
            nearest = Math.min(nearest, distanceToSegment(latitude, longitude, route.get(index - 1), route.get(index)));
        }
        return nearest;
    }

    private static double distanceToSegment(double latitude, double longitude, RoutePoint start, RoutePoint end) {
        double referenceLatitude = Math.toRadians((start.latitude() + end.latitude() + latitude) / 3.0);
        double pointX = Math.toRadians(longitude) * Math.cos(referenceLatitude) * EARTH_RADIUS_METRES;
        double pointY = Math.toRadians(latitude) * EARTH_RADIUS_METRES;
        double startX = Math.toRadians(start.longitude()) * Math.cos(referenceLatitude) * EARTH_RADIUS_METRES;
        double startY = Math.toRadians(start.latitude()) * EARTH_RADIUS_METRES;
        double endX = Math.toRadians(end.longitude()) * Math.cos(referenceLatitude) * EARTH_RADIUS_METRES;
        double endY = Math.toRadians(end.latitude()) * EARTH_RADIUS_METRES;
        double dx = endX - startX;
        double dy = endY - startY;
        if (dx == 0 && dy == 0) {
            return Math.hypot(pointX - startX, pointY - startY);
        }
        double ratio = ((pointX - startX) * dx + (pointY - startY) * dy) / (dx * dx + dy * dy);
        double clamped = Math.max(0, Math.min(1, ratio));
        return Math.hypot(pointX - (startX + clamped * dx), pointY - (startY + clamped * dy));
    }

    private static String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static UUID requiredId(UUID value) {
        if (value == null) {
            throw RiskException.invalidRequest();
        }
        return value;
    }

    private static String requiredText(String value) {
        if (value == null || value.isBlank()) {
            throw RiskException.invalidRequest();
        }
        return value.strip();
    }

    private static Instant databaseTime(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    public record TransitionCommand(UUID commandId, RiskIndicatorState targetState, String note) {}
}
