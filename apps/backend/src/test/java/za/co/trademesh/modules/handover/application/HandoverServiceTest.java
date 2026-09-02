package za.co.trademesh.modules.handover.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.trademesh.modules.handover.domain.CaptureMode;
import za.co.trademesh.modules.handover.domain.DeliveryDisputeResolution;
import za.co.trademesh.modules.handover.domain.HandoverAttempt;
import za.co.trademesh.modules.handover.domain.HandoverAttemptOutcome;
import za.co.trademesh.modules.handover.domain.HandoverChallenge;
import za.co.trademesh.modules.handover.domain.HandoverConfirmation;
import za.co.trademesh.modules.handover.domain.HandoverRepository;
import za.co.trademesh.modules.handover.domain.HandoverState;
import za.co.trademesh.modules.handover.domain.HandoverType;
import za.co.trademesh.modules.handover.domain.QuantityOutcome;
import za.co.trademesh.modules.shipment.application.ShipmentHandoverCatalog;
import za.co.trademesh.shared.events.DomainEvents;
import za.co.trademesh.shared.events.EventProperties;

class HandoverServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-02T08:00:00Z");
    private static final UUID BUSINESS_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID SHIPMENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID ORDER_ID = UUID.fromString("20000000-0000-0000-0000-000000000003");
    private static final UUID INITIATOR_ID = UUID.fromString("20000000-0000-0000-0000-000000000004");
    private static final UUID COUNTERPARTY_ID = UUID.fromString("20000000-0000-0000-0000-000000000005");
    private static final String TOKEN = "tmh_unit_test_opaque_token";

    private FakeHandoverRepository handovers;
    private FakeShipmentCatalog shipments;
    private FakeTokenGenerator tokenGenerator;
    private HandoverService service;

    @BeforeEach
    void setUp() {
        handovers = new FakeHandoverRepository();
        shipments = new FakeShipmentCatalog(ShipmentHandoverCatalog.Stage.AWAITING_COLLECTION);
        tokenGenerator = new FakeTokenGenerator();
        service = service(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void requiresBothExpectedPartiesAndCompletesCollectionOnlyOnce() {
        var issued = service.issue(
                BUSINESS_ID,
                SHIPMENT_ID,
                new HandoverService.IssueChallenge(HandoverType.COLLECTION, null, COUNTERPARTY_ID),
                INITIATOR_ID);

        assertThat(issued.qrPayload()).isEqualTo(TOKEN).doesNotContain(SHIPMENT_ID.toString());
        assertThat(issued.challenge().expectedLocation().latitude()).isEqualTo(-26.2041);

        UUID firstCommand = UUID.randomUUID();
        HandoverService.ConfirmHandover first = confirmation(firstCommand, QuantityOutcome.MATCHED, "20 cases");
        HandoverChallenge pending = service.confirm(first, INITIATOR_ID);
        HandoverChallenge retry = service.confirm(first, INITIATOR_ID);
        HandoverChallenge completed =
                service.confirm(confirmation(UUID.randomUUID(), QuantityOutcome.MATCHED, "20 cases"), COUNTERPARTY_ID);

        assertThat(pending.state()).isEqualTo(HandoverState.PENDING);
        assertThat(retry.confirmations()).hasSize(1);
        assertThat(completed.state()).isEqualTo(HandoverState.COMPLETED);
        assertThat(completed.confirmations()).hasSize(2);
        assertThat(shipments.completions).containsExactly(ShipmentHandoverCatalog.Completion.COLLECTION_VERIFIED);

        assertThatThrownBy(() -> service.confirm(
                        confirmation(UUID.randomUUID(), QuantityOutcome.MATCHED, "Replay"), COUNTERPARTY_ID))
                .isInstanceOf(HandoverException.class)
                .hasMessageContaining("cannot be reused");
        assertThat(handovers.attempts)
                .extracting(HandoverAttempt::outcome)
                .contains(HandoverAttemptOutcome.CHALLENGE_REPLAYED);
    }

    @Test
    void rejectsWrongPartyOfflineClockSkewAndDistantLocationWithRecordedOutcomes() {
        service.issue(
                BUSINESS_ID,
                SHIPMENT_ID,
                new HandoverService.IssueChallenge(HandoverType.COLLECTION, null, COUNTERPARTY_ID),
                INITIATOR_ID);

        UUID outsider = UUID.randomUUID();
        assertThatThrownBy(() ->
                        service.confirm(confirmation(UUID.randomUUID(), QuantityOutcome.MATCHED, "Outsider"), outsider))
                .isInstanceOf(HandoverException.class)
                .hasMessageContaining("not an expected participant");
        assertThatThrownBy(() -> service.confirm(
                        new HandoverService.ConfirmHandover(
                                UUID.randomUUID(),
                                TOKEN,
                                CaptureMode.OFFLINE,
                                NOW,
                                -26.2041,
                                28.0473,
                                QuantityOutcome.MATCHED,
                                "Offline"),
                        COUNTERPARTY_ID))
                .isInstanceOf(HandoverException.class)
                .hasMessageContaining("Offline");
        assertThatThrownBy(() -> service.confirm(
                        new HandoverService.ConfirmHandover(
                                UUID.randomUUID(),
                                TOKEN,
                                CaptureMode.ONLINE,
                                NOW.minus(Duration.ofMinutes(10)),
                                -26.2041,
                                28.0473,
                                QuantityOutcome.MATCHED,
                                "Old clock"),
                        COUNTERPARTY_ID))
                .isInstanceOf(HandoverException.class)
                .hasMessageContaining("device time");
        assertThatThrownBy(() -> service.confirm(
                        new HandoverService.ConfirmHandover(
                                UUID.randomUUID(),
                                TOKEN,
                                CaptureMode.ONLINE,
                                NOW,
                                -25.7479,
                                28.2293,
                                QuantityOutcome.MATCHED,
                                "Far away"),
                        COUNTERPARTY_ID))
                .isInstanceOf(HandoverException.class)
                .hasMessageContaining("outside");

        assertThat(handovers.attempts)
                .extracting(HandoverAttempt::outcome)
                .containsExactly(
                        HandoverAttemptOutcome.PARTICIPANT_MISMATCH,
                        HandoverAttemptOutcome.OFFLINE_NOT_ALLOWED,
                        HandoverAttemptOutcome.CLOCK_SKEW_EXCEEDED,
                        HandoverAttemptOutcome.OUTSIDE_LOCATION_TOLERANCE);
        assertThat(handovers.challenge.confirmations()).isEmpty();
    }

    @Test
    void recordsAQuantityDisputeAndUsesTheDeliveryDestination() {
        shipments.stage = ShipmentHandoverCatalog.Stage.IN_TRANSIT;
        var issued = service.issue(
                BUSINESS_ID,
                SHIPMENT_ID,
                new HandoverService.IssueChallenge(HandoverType.DELIVERY, ORDER_ID, COUNTERPARTY_ID),
                INITIATOR_ID);

        assertThat(issued.challenge().expectedLocation().label()).isEqualTo("Tembisa");
        service.confirm(deliveryConfirmation(UUID.randomUUID(), QuantityOutcome.MATCHED, "20 cases"), INITIATOR_ID);
        HandoverChallenge disputed = service.confirm(
                deliveryConfirmation(UUID.randomUUID(), QuantityOutcome.DISPUTED, "Receiver counted 19 cases"),
                COUNTERPARTY_ID);

        assertThat(disputed.state()).isEqualTo(HandoverState.DISPUTED);
        assertThat(disputed.hasQuantityDispute()).isTrue();
        assertThat(shipments.completions).containsExactly(ShipmentHandoverCatalog.Completion.DELIVERY_DISPUTED);
    }

    @Test
    void scansDeliveryQuantityIdempotentlyAndRequiresResolutionBeforeRelease() {
        shipments.stage = ShipmentHandoverCatalog.Stage.IN_TRANSIT;
        var issued = service.issue(
                BUSINESS_ID,
                SHIPMENT_ID,
                new HandoverService.IssueChallenge(HandoverType.DELIVERY, ORDER_ID, COUNTERPARTY_ID),
                INITIATOR_ID);
        UUID commandId = UUID.randomUUID();
        HandoverService.ScanDelivery scan = new HandoverService.ScanDelivery(
                commandId,
                issued.qrPayload(),
                new BigDecimal("19"),
                "https://objects.example.test/deliveries/count.jpg",
                -26.1000,
                28.2333);

        HandoverChallenge disputed = service.scanDelivery(SHIPMENT_ID, scan, COUNTERPARTY_ID);
        HandoverChallenge duplicate = service.scanDelivery(SHIPMENT_ID, scan, COUNTERPARTY_ID);

        assertThat(disputed.state()).isEqualTo(HandoverState.DISPUTED);
        assertThat(duplicate.confirmations()).hasSize(1);
        assertThat(disputed.confirmations().getFirst().capturedQuantity()).isEqualByComparingTo("19.0000");
        assertThat(disputed.confirmations().getFirst().photoUrl())
                .isEqualTo("https://objects.example.test/deliveries/count.jpg");
        assertThat(service.releaseAllowed(BUSINESS_ID, SHIPMENT_ID, List.of(ORDER_ID)))
                .isFalse();

        UUID resolutionCommand = UUID.randomUUID();
        var resolution =
                service.resolve(BUSINESS_ID, SHIPMENT_ID, resolutionCommand, new BigDecimal("7800"), INITIATOR_ID);
        var repeatedResolution =
                service.resolve(BUSINESS_ID, SHIPMENT_ID, resolutionCommand, new BigDecimal("7800"), INITIATOR_ID);

        assertThat(resolution.resolutionId()).isEqualTo(repeatedResolution.resolutionId());
        assertThat(resolution.resolvedAmount()).isEqualByComparingTo("7800.0000");
        assertThat(service.releaseAllowed(BUSINESS_ID, SHIPMENT_ID, List.of(ORDER_ID)))
                .isTrue();
        assertThat(service.deliveryStatus(BUSINESS_ID, SHIPMENT_ID).verificationStatus())
                .isEqualTo("RESOLVED");

        assertThatThrownBy(() -> service.scanDelivery(
                        SHIPMENT_ID,
                        new HandoverService.ScanDelivery(
                                UUID.randomUUID(), issued.qrPayload(), new BigDecimal("19"), null, -26.1000, 28.2333),
                        COUNTERPARTY_ID))
                .isInstanceOf(HandoverException.class)
                .hasMessageContaining("cannot be reused");
    }

    @Test
    void keepsAMultiStopShipmentMovingUntilEveryDeliveryIsVerified() {
        shipments.stage = ShipmentHandoverCatalog.Stage.IN_TRANSIT;
        shipments.includeSecondDelivery = true;
        service.issue(
                BUSINESS_ID,
                SHIPMENT_ID,
                new HandoverService.IssueChallenge(HandoverType.DELIVERY, ORDER_ID, COUNTERPARTY_ID),
                INITIATOR_ID);

        service.confirm(deliveryConfirmation(UUID.randomUUID(), QuantityOutcome.MATCHED, "20 cases"), INITIATOR_ID);
        HandoverChallenge firstStop = service.confirm(
                deliveryConfirmation(UUID.randomUUID(), QuantityOutcome.MATCHED, "20 cases"), COUNTERPARTY_ID);

        assertThat(firstStop.state()).isEqualTo(HandoverState.COMPLETED);
        assertThat(shipments.completions).isEmpty();
        assertThat(shipments.stage).isEqualTo(ShipmentHandoverCatalog.Stage.IN_TRANSIT);
    }

    @Test
    void expiresAnOldChallengeAndAllowsAReplacement() {
        service.issue(
                BUSINESS_ID,
                SHIPMENT_ID,
                new HandoverService.IssueChallenge(HandoverType.COLLECTION, null, COUNTERPARTY_ID),
                INITIATOR_ID);
        HandoverService later = service(Clock.fixed(NOW.plus(Duration.ofMinutes(6)), ZoneOffset.UTC));

        assertThatThrownBy(() -> later.confirm(
                        new HandoverService.ConfirmHandover(
                                UUID.randomUUID(),
                                TOKEN,
                                CaptureMode.ONLINE,
                                NOW.plus(Duration.ofMinutes(6)),
                                -26.2041,
                                28.0473,
                                QuantityOutcome.MATCHED,
                                "Late"),
                        INITIATOR_ID))
                .isInstanceOf(HandoverException.class)
                .hasMessageContaining("expired");
        assertThat(handovers.challenge.state()).isEqualTo(HandoverState.EXPIRED);

        var replacement = later.issue(
                BUSINESS_ID,
                SHIPMENT_ID,
                new HandoverService.IssueChallenge(HandoverType.COLLECTION, null, COUNTERPARTY_ID),
                INITIATOR_ID);
        assertThat(replacement.challenge().state()).isEqualTo(HandoverState.PENDING);
    }

    private HandoverService service(Clock clock) {
        DomainEvents events = new DomainEvents(ignored -> {}, clock, new EventProperties("handover-test"));
        return new HandoverService(
                handovers,
                shipments,
                (businessId, orderId) -> Optional.of(
                        new za.co.trademesh.modules.procurement.application.DeliveryOrderQuantityCatalog
                                .ExpectedQuantity(new BigDecimal("20.0000"), "CASE")),
                userId -> COUNTERPARTY_ID.equals(userId),
                tokenGenerator,
                properties(),
                events,
                clock);
    }

    private static HandoverProperties properties() {
        return new HandoverProperties(
                Duration.ofMinutes(5),
                Duration.ofMinutes(2),
                250,
                500,
                Duration.ofMinutes(30),
                "test-only-handover-signing-secret-32-characters");
    }

    private static HandoverService.ConfirmHandover confirmation(UUID commandId, QuantityOutcome outcome, String note) {
        return new HandoverService.ConfirmHandover(
                commandId, TOKEN, CaptureMode.ONLINE, NOW, -26.2041, 28.0473, outcome, note);
    }

    private static HandoverService.ConfirmHandover deliveryConfirmation(
            UUID commandId, QuantityOutcome outcome, String note) {
        return new HandoverService.ConfirmHandover(
                commandId, TOKEN, CaptureMode.ONLINE, NOW, -26.1000, 28.2333, outcome, note);
    }

    private static final class FakeShipmentCatalog implements ShipmentHandoverCatalog {
        private Stage stage;
        private boolean includeSecondDelivery;
        private final List<Completion> completions = new ArrayList<>();

        private FakeShipmentCatalog(Stage stage) {
            this.stage = stage;
        }

        @Override
        public Optional<HandoverShipment> findOwned(UUID businessId, UUID shipmentId) {
            if (!BUSINESS_ID.equals(businessId) || !SHIPMENT_ID.equals(shipmentId)) {
                return Optional.empty();
            }
            List<DeliveryStop> deliveryStops = new ArrayList<>();
            deliveryStops.add(new DeliveryStop(ORDER_ID, BUSINESS_ID, new Location("Tembisa", -26.1000, 28.2333)));
            if (includeSecondDelivery) {
                deliveryStops.add(new DeliveryStop(
                        UUID.fromString("20000000-0000-0000-0000-000000000006"),
                        BUSINESS_ID,
                        new Location("Pretoria", -25.7479, 28.2293)));
            }
            return Optional.of(new HandoverShipment(
                    SHIPMENT_ID,
                    BUSINESS_ID,
                    stage,
                    new Location("Collection", -26.2041, 28.0473),
                    deliveryStops,
                    NOW));
        }

        @Override
        public void complete(
                UUID businessId,
                UUID shipmentId,
                UUID commandId,
                Completion completion,
                String reason,
                UUID correlationId,
                UUID actorUserId) {
            completions.add(completion);
            stage = completion == Completion.COLLECTION_VERIFIED ? Stage.COLLECTED : Stage.TERMINAL;
        }
    }

    private static final class FakeHandoverRepository implements HandoverRepository {
        private HandoverChallenge challenge;
        private DeliveryDisputeResolution resolution;
        private final Map<UUID, HandoverConfirmation> confirmationsByCommand = new HashMap<>();
        private final List<HandoverAttempt> attempts = new ArrayList<>();

        @Override
        public int expireActive(UUID shipmentId, HandoverType type, UUID deliveryOrderId, Instant now) {
            if (challenge != null && challenge.state() == HandoverState.PENDING && now.isAfter(challenge.expiresAt())) {
                challenge = state(challenge, HandoverState.EXPIRED, now);
                return 1;
            }
            return 0;
        }

        @Override
        public Optional<HandoverChallenge> findActive(UUID shipmentId, HandoverType type, UUID deliveryOrderId) {
            return Optional.ofNullable(challenge).filter(value -> value.state() == HandoverState.PENDING);
        }

        @Override
        public boolean save(HandoverChallenge value) {
            if (challenge != null && challenge.state() == HandoverState.PENDING) {
                return false;
            }
            challenge = value;
            return true;
        }

        @Override
        public Optional<HandoverChallenge> findOwned(UUID businessId, UUID shipmentId, UUID challengeId) {
            return Optional.ofNullable(challenge)
                    .filter(value -> value.businessId().equals(businessId))
                    .filter(value -> value.shipmentId().equals(shipmentId))
                    .filter(value -> value.id().equals(challengeId));
        }

        @Override
        public List<HandoverChallenge> findByShipment(UUID shipmentId) {
            return Optional.ofNullable(challenge)
                    .filter(value -> value.shipmentId().equals(shipmentId))
                    .stream()
                    .toList();
        }

        @Override
        public Optional<HandoverChallenge> findByNonceHashForUpdate(String nonceHash) {
            return Optional.ofNullable(challenge)
                    .filter(value -> value.nonceHash().equals(nonceHash));
        }

        @Override
        public Optional<HandoverConfirmation> findConfirmationByCommandId(UUID commandId) {
            return Optional.ofNullable(confirmationsByCommand.get(commandId));
        }

        @Override
        public boolean saveConfirmation(HandoverConfirmation confirmation) {
            if (challenge.confirmations().stream().anyMatch(existing -> existing.party() == confirmation.party())) {
                return false;
            }
            List<HandoverConfirmation> confirmations = new ArrayList<>(challenge.confirmations());
            confirmations.add(confirmation);
            challenge = copy(challenge, challenge.state(), challenge.completedAt(), confirmations);
            confirmationsByCommand.put(confirmation.commandId(), confirmation);
            return true;
        }

        @Override
        public boolean changeState(
                UUID challengeId, HandoverState expected, HandoverState target, Instant completedAt) {
            if (challenge == null || !challenge.id().equals(challengeId) || challenge.state() != expected) {
                return false;
            }
            challenge = state(challenge, target, completedAt);
            return true;
        }

        @Override
        public Set<UUID> findFinalizedDeliveryOrderIds(UUID shipmentId) {
            if (challenge != null
                    && challenge.type() == HandoverType.DELIVERY
                    && (challenge.state() == HandoverState.COMPLETED || challenge.state() == HandoverState.DISPUTED)) {
                return Set.of(challenge.deliveryOrderId());
            }
            return Set.of();
        }

        @Override
        public Optional<DeliveryDisputeResolution> findResolution(UUID businessId, UUID shipmentId) {
            return Optional.ofNullable(resolution)
                    .filter(value -> value.businessId().equals(businessId))
                    .filter(value -> value.shipmentId().equals(shipmentId));
        }

        @Override
        public Optional<DeliveryDisputeResolution> findResolutionByCommandId(UUID commandId) {
            return Optional.ofNullable(resolution)
                    .filter(value -> value.commandId().equals(commandId));
        }

        @Override
        public boolean saveResolution(DeliveryDisputeResolution value) {
            if (resolution != null) {
                return false;
            }
            resolution = value;
            return true;
        }

        @Override
        public void saveAttempt(HandoverAttempt attempt) {
            attempts.add(attempt);
        }

        private static HandoverChallenge state(HandoverChallenge source, HandoverState state, Instant completedAt) {
            return copy(source, state, completedAt, source.confirmations());
        }

        private static HandoverChallenge copy(
                HandoverChallenge source,
                HandoverState state,
                Instant completedAt,
                List<HandoverConfirmation> confirmations) {
            return new HandoverChallenge(
                    source.id(),
                    source.shipmentId(),
                    source.businessId(),
                    source.type(),
                    source.deliveryOrderId(),
                    state,
                    source.nonceHash(),
                    source.initiatorUserId(),
                    source.counterpartyUserId(),
                    source.expectedLocation(),
                    source.expectedQuantity(),
                    source.unitOfMeasure(),
                    source.locationToleranceMetres(),
                    source.expiresAt(),
                    completedAt,
                    source.correlationId(),
                    source.createdAt(),
                    confirmations);
        }
    }

    private static final class FakeTokenGenerator implements HandoverTokenGenerator {
        private TokenClaims claims;

        @Override
        public String generate(TokenClaims value) {
            claims = value;
            return TOKEN;
        }

        @Override
        public TokenClaims verify(String token) {
            if (!TOKEN.equals(token) || claims == null) {
                throw HandoverException.invalidToken();
            }
            return claims;
        }
    }
}
