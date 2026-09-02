package za.co.trademesh.modules.risk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.trademesh.modules.risk.domain.RiskEvidenceReference;
import za.co.trademesh.modules.risk.domain.RiskIndicator;
import za.co.trademesh.modules.risk.domain.RiskIndicatorState;
import za.co.trademesh.modules.risk.domain.RiskIndicatorTransition;
import za.co.trademesh.modules.risk.domain.RiskRepository;
import za.co.trademesh.modules.risk.domain.RiskRule;
import za.co.trademesh.modules.shipment.application.ShipmentRiskCatalog;
import za.co.trademesh.modules.shipment.application.ShipmentRiskCatalog.RiskPhase;
import za.co.trademesh.modules.shipment.application.ShipmentRiskCatalog.RoutePoint;
import za.co.trademesh.modules.telemetry.application.TelemetryRiskCatalog;
import za.co.trademesh.modules.telemetry.application.TelemetryRiskCatalog.RiskReading;
import za.co.trademesh.shared.events.DomainEvents;
import za.co.trademesh.shared.events.EventProperties;

class RiskServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-02T08:00:00Z");
    private static final UUID SHIPMENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID BUSINESS_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID DRIVER_ID = UUID.fromString("10000000-0000-0000-0000-000000000004");

    private FakeRiskRepository risks;
    private FakeTelemetryCatalog telemetry;
    private FakeShipmentCatalog shipments;
    private RiskService service;

    @BeforeEach
    void setUp() {
        risks = new FakeRiskRepository();
        telemetry = new FakeTelemetryCatalog();
        shipments = new FakeShipmentCatalog(snapshot(RiskPhase.MOVING, NOW.minus(Duration.ofHours(1))));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        DomainEvents events = new DomainEvents(ignored -> {}, clock, new EventProperties("risk-test"));
        service = new RiskService(risks, telemetry, shipments, properties(), events, clock);
    }

    @Test
    void createsExplainableIndicatorsForEveryTelemetryAndTimeBasedRuleWithoutActiveDuplicates() {
        UUID firstDevice = UUID.randomUUID();
        UUID replacementDevice = UUID.randomUUID();
        RiskReading earlier = reading(
                UUID.randomUUID(), firstDevice, NOW.minus(Duration.ofMinutes(20)), -25.0000, 30.0000, "0", null, false);
        RiskReading stoppedFifteenMinutesAgo = reading(
                UUID.randomUUID(), firstDevice, NOW.minus(Duration.ofMinutes(15)), -25.0, 30.0, "0", null, false);
        RiskReading fuelBaseline = reading(
                UUID.randomUUID(),
                firstDevice,
                NOW.minus(Duration.ofMinutes(10)),
                -25.0001,
                30.0001,
                "0",
                "310",
                false);
        RiskReading stoppedFiveMinutesAgo = reading(
                UUID.randomUUID(), firstDevice, NOW.minus(Duration.ofMinutes(5)), -25.0, 30.0, "0", null, false);
        RiskReading current = reading(UUID.randomUUID(), replacementDevice, NOW, -25.0001, 30.0001, "0", "265", true);
        telemetry.readings.put(earlier.id(), earlier);
        telemetry.readings.put(stoppedFifteenMinutesAgo.id(), stoppedFifteenMinutesAgo);
        telemetry.readings.put(fuelBaseline.id(), fuelBaseline);
        telemetry.readings.put(stoppedFiveMinutesAgo.id(), stoppedFiveMinutesAgo);
        telemetry.readings.put(current.id(), current);
        telemetry.window = List.of(current, stoppedFiveMinutesAgo, fuelBaseline, stoppedFifteenMinutesAgo, earlier);
        telemetry.offline =
                List.of(new TelemetryRiskCatalog.OfflineDevice(firstDevice, SHIPMENT_ID, NOW.minusSeconds(901)));

        service.evaluateTelemetry(current.id());
        service.evaluateTelemetry(current.id());
        service.evaluateDriverChange(SHIPMENT_ID, UUID.randomUUID(), ASSIGNMENT_ID, DRIVER_ID, NOW);
        service.evaluateTimeBasedRules();
        service.evaluateTimeBasedRules();

        assertThat(risks.byRule.keySet())
                .containsExactlyInAnyOrder(
                        RiskRule.ROUTE_DEVIATION,
                        RiskRule.UNEXPECTED_STOP,
                        RiskRule.STATIONARY_FUEL_DROP,
                        RiskRule.UNEXPECTED_SEAL_OPENING,
                        RiskRule.TELEMETRY_DEVICE_CHANGED,
                        RiskRule.DRIVER_ASSIGNMENT_CHANGED,
                        RiskRule.TRACKER_OFFLINE,
                        RiskRule.DELIVERY_DELAY);
        assertThat(risks.byRule.values()).allSatisfy(indicator -> {
            assertThat(indicator.ruleVersion()).isEqualTo("operational-risk/v1");
            assertThat(indicator.evidence()).isNotEmpty();
            assertThat(indicator.transitions()).hasSize(1);
            assertThat(indicator.explanation().toLowerCase()).doesNotContain("fraud", "theft");
        });
        assertThat(risks.byRule.get(RiskRule.ROUTE_DEVIATION).evidence()).hasSize(2);
        assertThat(risks.byRule.get(RiskRule.STATIONARY_FUEL_DROP).explanation())
                .contains("45 litres");
    }

    @Test
    void ignoresMissingOrNoisyEvidenceInsteadOfGuessing() {
        RiskReading current = reading(UUID.randomUUID(), UUID.randomUUID(), NOW, -25.0, 30.0, null, null, null);
        RiskReading staleStopStart = reading(
                UUID.randomUUID(), current.deviceId(), NOW.minus(Duration.ofMinutes(25)), null, null, "0", "310", null);
        RiskReading staleStopEnd = reading(
                UUID.randomUUID(), current.deviceId(), NOW.minus(Duration.ofMinutes(20)), null, null, "0", null, null);
        telemetry.readings.put(current.id(), current);
        telemetry.window = List.of(current, staleStopEnd, staleStopStart);

        service.evaluateTelemetry(current.id());

        assertThat(risks.byRule).isEmpty();
    }

    @Test
    void keepsAnAppendOnlyHumanReviewHistoryAndMakesCommandsIdempotent() {
        RiskReading reading = reading(UUID.randomUUID(), UUID.randomUUID(), NOW, -26.15, 28.05, "0", "100", true);
        telemetry.readings.put(reading.id(), reading);
        telemetry.window = List.of(reading);
        service.evaluateTelemetry(reading.id());
        RiskIndicator indicator = risks.byRule.get(RiskRule.UNEXPECTED_SEAL_OPENING);
        UUID actor = UUID.randomUUID();
        UUID acknowledgeCommand = UUID.randomUUID();

        RiskIndicator acknowledged = service.transition(
                indicator.id(),
                new RiskService.TransitionCommand(
                        acknowledgeCommand, RiskIndicatorState.ACKNOWLEDGED, "Driver contacted."),
                actor);
        RiskIndicator replayed = service.transition(
                indicator.id(),
                new RiskService.TransitionCommand(
                        acknowledgeCommand, RiskIndicatorState.ACKNOWLEDGED, "Driver contacted."),
                actor);
        RiskIndicator investigating = service.transition(
                indicator.id(),
                new RiskService.TransitionCommand(
                        UUID.randomUUID(), RiskIndicatorState.INVESTIGATING, "Checking handover records."),
                actor);
        RiskIndicator resolved = service.transition(
                indicator.id(),
                new RiskService.TransitionCommand(
                        UUID.randomUUID(), RiskIndicatorState.RESOLVED, "Seal was opened at authorised delivery."),
                actor);

        assertThat(acknowledged.state()).isEqualTo(RiskIndicatorState.ACKNOWLEDGED);
        assertThat(replayed.transitions()).hasSize(2);
        assertThat(investigating.state()).isEqualTo(RiskIndicatorState.INVESTIGATING);
        assertThat(resolved.state()).isEqualTo(RiskIndicatorState.RESOLVED);
        assertThat(resolved.transitions())
                .extracting(RiskIndicatorTransition::toState)
                .containsExactly(
                        RiskIndicatorState.OPEN,
                        RiskIndicatorState.ACKNOWLEDGED,
                        RiskIndicatorState.INVESTIGATING,
                        RiskIndicatorState.RESOLVED);
        assertThatThrownBy(() -> service.transition(
                        indicator.id(),
                        new RiskService.TransitionCommand(
                                UUID.randomUUID(), RiskIndicatorState.FALSE_POSITIVE, "Too late."),
                        actor))
                .isInstanceOf(RiskException.class)
                .hasMessageContaining("cannot move");
    }

    @Test
    void calculatesDistanceToTheRouteRatherThanOnlyItsEndpoints() {
        double distance = RiskService.distanceFromRoute(
                -26.15, 28.05, List.of(new RoutePoint(-26.10, 28.00), new RoutePoint(-26.20, 28.10)));

        assertThat(distance).isLessThan(20);
    }

    private static RiskProperties properties() {
        return new RiskProperties(
                1000,
                2,
                new BigDecimal("2"),
                Duration.ofMinutes(20),
                Duration.ofMinutes(5),
                Duration.ofMinutes(15),
                new BigDecimal("30"),
                Duration.ofMinutes(20),
                Duration.ofMinutes(30),
                Duration.ofMinutes(1),
                100,
                1000,
                "operational-risk/v1");
    }

    private static ShipmentRiskCatalog.ShipmentRiskSnapshot snapshot(RiskPhase phase, Instant deadline) {
        return new ShipmentRiskCatalog.ShipmentRiskSnapshot(
                SHIPMENT_ID,
                BUSINESS_ID,
                phase,
                ASSIGNMENT_ID,
                DRIVER_ID,
                UUID.randomUUID(),
                List.of(new RoutePoint(-26.10, 28.00), new RoutePoint(-26.20, 28.10)),
                deadline);
    }

    private static RiskReading reading(
            UUID id,
            UUID deviceId,
            Instant recordedAt,
            Double latitude,
            Double longitude,
            String speed,
            String fuel,
            Boolean sealOpen) {
        return new RiskReading(
                id,
                deviceId,
                SHIPMENT_ID,
                recordedAt,
                NOW,
                latitude,
                longitude,
                decimal(speed),
                decimal(fuel),
                sealOpen);
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static final class FakeTelemetryCatalog implements TelemetryRiskCatalog {
        private final Map<UUID, RiskReading> readings = new HashMap<>();
        private List<RiskReading> window = List.of();
        private List<OfflineDevice> offline = List.of();

        @Override
        public Optional<RiskReading> findReading(UUID readingId) {
            return Optional.ofNullable(readings.get(readingId));
        }

        @Override
        public List<RiskReading> findReadingsThrough(UUID shipmentId, Instant from, Instant through, int limit) {
            return window;
        }

        @Override
        public List<OfflineDevice> findOfflineDevices(Instant lastSeenBefore, int limit) {
            return offline;
        }
    }

    private static final class FakeShipmentCatalog implements ShipmentRiskCatalog {
        private final ShipmentRiskSnapshot snapshot;

        private FakeShipmentCatalog(ShipmentRiskSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public Optional<ShipmentRiskSnapshot> find(UUID shipmentId) {
            return snapshot.shipmentId().equals(shipmentId) ? Optional.of(snapshot) : Optional.empty();
        }

        @Override
        public List<ShipmentRiskSnapshot> findOperational(int limit) {
            return List.of(snapshot);
        }
    }

    private static final class FakeRiskRepository implements RiskRepository {
        private final Map<RiskRule, RiskIndicator> byRule = new LinkedHashMap<>();
        private final Map<UUID, RiskIndicatorTransition> transitionsByCommand = new HashMap<>();

        @Override
        public RiskIndicator upsertActive(RiskIndicator proposal) {
            RiskIndicator current = byRule.get(proposal.rule());
            if (current == null || !current.state().isActive()) {
                byRule.put(proposal.rule(), proposal);
                proposal.transitions()
                        .forEach(transition -> transitionsByCommand.put(transition.commandId(), transition));
                return proposal;
            }
            Map<String, RiskEvidenceReference> evidence = new LinkedHashMap<>();
            current.evidence().forEach(item -> evidence.put(key(item), item));
            proposal.evidence().forEach(item -> evidence.put(key(item), item));
            RiskIndicator updated = copy(
                    current,
                    current.state(),
                    current.transitions(),
                    evidence.values().stream().toList(),
                    proposal.lastObservedAt());
            byRule.put(current.rule(), updated);
            return updated;
        }

        @Override
        public Optional<RiskIndicator> findById(UUID indicatorId) {
            return byRule.values().stream()
                    .filter(value -> value.id().equals(indicatorId))
                    .findFirst();
        }

        @Override
        public Optional<RiskIndicator> findByIdForUpdate(UUID indicatorId) {
            return findById(indicatorId);
        }

        @Override
        public List<RiskIndicator> findByShipment(UUID shipmentId) {
            return byRule.values().stream()
                    .filter(value -> value.shipmentId().equals(shipmentId))
                    .sorted(Comparator.comparing(RiskIndicator::firstObservedAt).reversed())
                    .toList();
        }

        @Override
        public Optional<RiskIndicatorTransition> findTransitionByCommandId(UUID commandId) {
            return Optional.ofNullable(transitionsByCommand.get(commandId));
        }

        @Override
        public boolean transition(
                UUID indicatorId,
                RiskIndicatorState expectedState,
                RiskIndicatorTransition transition,
                Instant updatedAt) {
            RiskIndicator current = findById(indicatorId).orElseThrow();
            if (current.state() != expectedState) {
                return false;
            }
            List<RiskIndicatorTransition> history = new ArrayList<>(current.transitions());
            history.add(transition);
            RiskIndicator updated =
                    copy(current, transition.toState(), history, current.evidence(), current.lastObservedAt());
            byRule.put(current.rule(), updated);
            transitionsByCommand.put(transition.commandId(), transition);
            return true;
        }

        private static RiskIndicator copy(
                RiskIndicator source,
                RiskIndicatorState state,
                List<RiskIndicatorTransition> transitions,
                List<RiskEvidenceReference> evidence,
                Instant lastObservedAt) {
            return new RiskIndicator(
                    source.id(),
                    source.shipmentId(),
                    source.businessId(),
                    source.rule(),
                    source.ruleVersion(),
                    source.severity(),
                    source.explanation(),
                    state,
                    source.firstObservedAt(),
                    lastObservedAt,
                    source.createdAt(),
                    NOW,
                    evidence,
                    transitions);
        }

        private static String key(RiskEvidenceReference evidence) {
            return evidence.evidenceType() + evidence.evidenceId();
        }
    }
}
