package za.co.trademesh.modules.handover.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.access.application.ActiveUserDirectory;
import za.co.trademesh.modules.handover.domain.CaptureMode;
import za.co.trademesh.modules.handover.domain.HandoverAttempt;
import za.co.trademesh.modules.handover.domain.HandoverAttemptOutcome;
import za.co.trademesh.modules.handover.domain.HandoverChallenge;
import za.co.trademesh.modules.handover.domain.HandoverConfirmation;
import za.co.trademesh.modules.handover.domain.HandoverLocation;
import za.co.trademesh.modules.handover.domain.HandoverParty;
import za.co.trademesh.modules.handover.domain.HandoverRepository;
import za.co.trademesh.modules.handover.domain.HandoverState;
import za.co.trademesh.modules.handover.domain.HandoverType;
import za.co.trademesh.modules.handover.domain.QuantityOutcome;
import za.co.trademesh.modules.handover.events.HandoverEvent;
import za.co.trademesh.modules.shipment.application.ShipmentHandoverCatalog;
import za.co.trademesh.modules.shipment.application.ShipmentHandoverCatalog.Completion;
import za.co.trademesh.modules.shipment.application.ShipmentHandoverCatalog.DeliveryStop;
import za.co.trademesh.modules.shipment.application.ShipmentHandoverCatalog.HandoverShipment;
import za.co.trademesh.modules.shipment.application.ShipmentHandoverCatalog.Stage;
import za.co.trademesh.shared.events.DomainEvents;

@Service
public class HandoverService {

    private static final double EARTH_RADIUS_METRES = 6_371_000;

    private final HandoverRepository handovers;
    private final ShipmentHandoverCatalog shipments;
    private final ActiveUserDirectory users;
    private final HandoverTokenGenerator tokens;
    private final HandoverProperties properties;
    private final DomainEvents events;
    private final Clock clock;

    public HandoverService(
            HandoverRepository handovers,
            ShipmentHandoverCatalog shipments,
            ActiveUserDirectory users,
            HandoverTokenGenerator tokens,
            HandoverProperties properties,
            DomainEvents events,
            Clock clock) {
        this.handovers = handovers;
        this.shipments = shipments;
        this.users = users;
        this.tokens = tokens;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public IssuedChallenge issue(UUID businessId, UUID shipmentId, IssueChallenge command, UUID actorUserId) {
        UUID owner = requiredId(businessId);
        UUID shipmentReference = requiredId(shipmentId);
        UUID actor = requiredId(actorUserId);
        if (command == null || command.type() == null) {
            throw HandoverException.invalidRequest();
        }
        UUID counterparty = requiredId(command.counterpartyUserId());
        if (actor.equals(counterparty) || !users.isActive(counterparty)) {
            throw HandoverException.invalidRequest();
        }
        HandoverShipment shipment =
                shipments.findOwned(owner, shipmentReference).orElseThrow(HandoverException::notFound);
        HandoverLocation location = expectedLocation(shipment, command.type(), command.deliveryOrderId());
        Instant now = databaseTime(clock.instant());
        handovers.expireActive(shipmentReference, command.type(), command.deliveryOrderId(), now);
        if (handovers
                .findActive(shipmentReference, command.type(), command.deliveryOrderId())
                .isPresent()) {
            throw HandoverException.activeChallengeExists();
        }

        String qrPayload = tokens.generate();
        HandoverChallenge challenge = new HandoverChallenge(
                UUID.randomUUID(),
                shipmentReference,
                owner,
                command.type(),
                command.deliveryOrderId(),
                HandoverState.PENDING,
                hash(qrPayload),
                actor,
                counterparty,
                location,
                properties.locationToleranceMetres(),
                now.plus(properties.challengeTtl()),
                null,
                UUID.randomUUID(),
                now,
                List.of());
        if (!handovers.save(challenge)) {
            throw HandoverException.activeChallengeExists();
        }
        events.publish(
                new HandoverEvent.ChallengeIssued(challenge.id(), challenge.shipmentId(), challenge.type()),
                actor.toString());
        return new IssuedChallenge(challenge, qrPayload);
    }

    @Transactional
    public HandoverChallenge get(UUID businessId, UUID shipmentId, UUID challengeId) {
        HandoverChallenge current = handovers
                .findOwned(requiredId(businessId), requiredId(shipmentId), requiredId(challengeId))
                .orElseThrow(HandoverException::notFound);
        Instant now = databaseTime(clock.instant());
        if (current.state() == HandoverState.PENDING && now.isAfter(current.expiresAt())) {
            handovers.changeState(current.id(), HandoverState.PENDING, HandoverState.EXPIRED, now);
            return handovers
                    .findOwned(current.businessId(), current.shipmentId(), current.id())
                    .orElseThrow(HandoverException::notFound);
        }
        return current;
    }

    @Transactional(noRollbackFor = HandoverException.class)
    public HandoverChallenge confirm(ConfirmHandover command, UUID actorUserId) {
        UUID actor = requiredId(actorUserId);
        ConfirmHandover normalized = normalize(command);
        String nonceHash = hash(normalized.qrPayload());
        HandoverChallenge current =
                handovers.findByNonceHashForUpdate(nonceHash).orElseThrow(HandoverException::invalidToken);
        Instant now = databaseTime(clock.instant());

        if (current.state().terminal()) {
            HandoverAttemptOutcome outcome = current.state() == HandoverState.EXPIRED
                    ? HandoverAttemptOutcome.CHALLENGE_EXPIRED
                    : HandoverAttemptOutcome.CHALLENGE_REPLAYED;
            throw reject(
                    current,
                    normalized,
                    actor,
                    outcome,
                    current.state() == HandoverState.EXPIRED
                            ? HandoverException.expired()
                            : HandoverException.replayed(),
                    now);
        }
        if (now.isAfter(current.expiresAt())) {
            handovers.changeState(current.id(), HandoverState.PENDING, HandoverState.EXPIRED, now);
            throw reject(
                    current,
                    normalized,
                    actor,
                    HandoverAttemptOutcome.CHALLENGE_EXPIRED,
                    HandoverException.expired(),
                    now);
        }

        HandoverParty party = party(current, actor)
                .orElseThrow(() -> reject(
                        current,
                        normalized,
                        actor,
                        HandoverAttemptOutcome.PARTICIPANT_MISMATCH,
                        HandoverException.participantMismatch(),
                        now));
        String fingerprint = fingerprint(normalized, actor);
        Optional<HandoverConfirmation> prior = handovers.findConfirmationByCommandId(normalized.commandId());
        if (prior.isPresent()) {
            HandoverConfirmation existing = prior.get();
            if (!existing.challengeId().equals(current.id())
                    || !existing.actorUserId().equals(actor)
                    || !existing.inputFingerprint().equals(fingerprint)) {
                throw HandoverException.commandConflict();
            }
            return current;
        }
        if (current.confirmedBy(party)) {
            throw reject(
                    current,
                    normalized,
                    actor,
                    HandoverAttemptOutcome.PARTY_ALREADY_CONFIRMED,
                    HandoverException.partyAlreadyConfirmed(),
                    now);
        }
        if (normalized.captureMode() == CaptureMode.OFFLINE) {
            throw reject(
                    current,
                    normalized,
                    actor,
                    HandoverAttemptOutcome.OFFLINE_NOT_ALLOWED,
                    HandoverException.offlineNotAllowed(),
                    now);
        }
        if (Duration.between(normalized.observedAt(), now).abs().compareTo(properties.allowedClockSkew()) > 0) {
            throw reject(
                    current,
                    normalized,
                    actor,
                    HandoverAttemptOutcome.CLOCK_SKEW_EXCEEDED,
                    HandoverException.clockSkew(),
                    now);
        }
        HandoverShipment shipment = shipments
                .findOwned(current.businessId(), current.shipmentId())
                .filter(value -> validState(value, current))
                .orElseThrow(() -> reject(
                        current,
                        normalized,
                        actor,
                        HandoverAttemptOutcome.SHIPMENT_STATE_CONFLICT,
                        HandoverException.stateConflict(),
                        now));
        double distance = distanceMetres(
                normalized.latitude(),
                normalized.longitude(),
                current.expectedLocation().latitude(),
                current.expectedLocation().longitude());
        if (distance > current.locationToleranceMetres()) {
            throw reject(
                    current,
                    normalized,
                    actor,
                    HandoverAttemptOutcome.OUTSIDE_LOCATION_TOLERANCE,
                    HandoverException.outsideLocation(),
                    now);
        }

        HandoverConfirmation confirmation = new HandoverConfirmation(
                UUID.randomUUID(),
                current.id(),
                normalized.commandId(),
                fingerprint,
                actor,
                party,
                normalized.observedAt(),
                now,
                normalized.latitude(),
                normalized.longitude(),
                distance,
                normalized.quantityOutcome(),
                normalized.quantityNote());
        if (!handovers.saveConfirmation(confirmation)) {
            throw HandoverException.partyAlreadyConfirmed();
        }
        saveAttempt(current, normalized, actor, HandoverAttemptOutcome.ACCEPTED, "Confirmation accepted.", now);
        events.publish(
                new HandoverEvent.ConfirmationAccepted(current.id(), current.shipmentId(), party), actor.toString());

        HandoverChallenge confirmed =
                handovers.findByNonceHashForUpdate(nonceHash).orElseThrow();
        if (confirmed.confirmations().size() < 2) {
            return confirmed;
        }
        HandoverState outcome = confirmed.hasQuantityDispute() ? HandoverState.DISPUTED : HandoverState.COMPLETED;
        if (!handovers.changeState(confirmed.id(), HandoverState.PENDING, outcome, now)) {
            throw HandoverException.replayed();
        }
        completeShipment(confirmed, outcome, actor, shipment);
        events.publish(
                new HandoverEvent.HandoverFinalized(confirmed.id(), confirmed.shipmentId(), confirmed.type(), outcome),
                actor.toString());
        return handovers.findByNonceHashForUpdate(nonceHash).orElseThrow();
    }

    private HandoverLocation expectedLocation(HandoverShipment shipment, HandoverType type, UUID deliveryOrderId) {
        if (type == HandoverType.COLLECTION) {
            if (deliveryOrderId != null || shipment.stage() != Stage.AWAITING_COLLECTION) {
                throw HandoverException.stateConflict();
            }
            return location(shipment.collectionLocation());
        }
        if (deliveryOrderId == null || (shipment.stage() != Stage.IN_TRANSIT && shipment.stage() != Stage.DELAYED)) {
            throw HandoverException.stateConflict();
        }
        return shipment.deliveryStops().stream()
                .filter(stop -> stop.orderId().equals(deliveryOrderId))
                .findFirst()
                .map(DeliveryStop::location)
                .map(HandoverService::location)
                .orElseThrow(HandoverException::invalidRequest);
    }

    private void completeShipment(
            HandoverChallenge challenge, HandoverState outcome, UUID actor, HandoverShipment shipment) {
        if (outcome == HandoverState.DISPUTED && challenge.type() == HandoverType.COLLECTION) {
            return;
        }
        if (challenge.type() == HandoverType.DELIVERY
                && outcome == HandoverState.COMPLETED
                && !handovers
                        .findFinalizedDeliveryOrderIds(challenge.shipmentId())
                        .containsAll(shipment.deliveryStops().stream()
                                .map(DeliveryStop::orderId)
                                .toList())) {
            return;
        }
        Completion completion =
                switch (challenge.type()) {
                    case COLLECTION -> Completion.COLLECTION_VERIFIED;
                    case DELIVERY ->
                        outcome == HandoverState.DISPUTED ? Completion.DELIVERY_DISPUTED : Completion.DELIVERY_VERIFIED;
                };
        String reason =
                switch (completion) {
                    case COLLECTION_VERIFIED -> "Collection verified by both handover parties.";
                    case DELIVERY_VERIFIED -> "Delivery verified by both handover parties.";
                    case DELIVERY_DISPUTED -> "Delivery handover recorded a quantity dispute.";
                };
        shipments.complete(
                challenge.businessId(),
                challenge.shipmentId(),
                challenge.id(),
                completion,
                reason,
                challenge.correlationId(),
                actor);
    }

    private HandoverException reject(
            HandoverChallenge challenge,
            ConfirmHandover command,
            UUID actor,
            HandoverAttemptOutcome outcome,
            HandoverException exception,
            Instant now) {
        saveAttempt(challenge, command, actor, outcome, exception.getMessage(), now);
        return exception;
    }

    private void saveAttempt(
            HandoverChallenge challenge,
            ConfirmHandover command,
            UUID actor,
            HandoverAttemptOutcome outcome,
            String detail,
            Instant now) {
        handovers.saveAttempt(new HandoverAttempt(
                UUID.randomUUID(),
                challenge.id(),
                actor,
                outcome,
                now,
                command.observedAt(),
                command.latitude(),
                command.longitude(),
                detail));
    }

    private static boolean validState(HandoverShipment shipment, HandoverChallenge challenge) {
        if (challenge.type() == HandoverType.COLLECTION) {
            return shipment.stage() == Stage.AWAITING_COLLECTION;
        }
        return (shipment.stage() == Stage.IN_TRANSIT || shipment.stage() == Stage.DELAYED)
                && shipment.deliveryStops().stream()
                        .anyMatch(stop -> stop.orderId().equals(challenge.deliveryOrderId()));
    }

    private static Optional<HandoverParty> party(HandoverChallenge challenge, UUID actor) {
        if (challenge.initiatorUserId().equals(actor)) {
            return Optional.of(HandoverParty.INITIATOR);
        }
        if (challenge.counterpartyUserId().equals(actor)) {
            return Optional.of(HandoverParty.COUNTERPARTY);
        }
        return Optional.empty();
    }

    private ConfirmHandover normalize(ConfirmHandover command) {
        if (command == null
                || command.commandId() == null
                || command.captureMode() == null
                || command.observedAt() == null
                || command.quantityOutcome() == null
                || !coordinate(command.latitude(), command.longitude())) {
            throw HandoverException.invalidRequest();
        }
        String qrPayload = requiredText(command.qrPayload());
        if (qrPayload.length() > 128) {
            throw HandoverException.invalidRequest();
        }
        String note =
                command.quantityNote() == null ? "" : command.quantityNote().strip();
        if (note.length() > properties.maxQuantityNoteLength()
                || (command.quantityOutcome() == QuantityOutcome.DISPUTED && note.isBlank())) {
            throw HandoverException.invalidRequest();
        }
        return new ConfirmHandover(
                command.commandId(),
                qrPayload,
                command.captureMode(),
                databaseTime(command.observedAt()),
                command.latitude(),
                command.longitude(),
                command.quantityOutcome(),
                note);
    }

    static double distanceMetres(double fromLatitude, double fromLongitude, double toLatitude, double toLongitude) {
        double firstLatitude = Math.toRadians(fromLatitude);
        double secondLatitude = Math.toRadians(toLatitude);
        double latitudeDifference = Math.toRadians(toLatitude - fromLatitude);
        double longitudeDifference = Math.toRadians(toLongitude - fromLongitude);
        double value = Math.sin(latitudeDifference / 2) * Math.sin(latitudeDifference / 2)
                + Math.cos(firstLatitude)
                        * Math.cos(secondLatitude)
                        * Math.sin(longitudeDifference / 2)
                        * Math.sin(longitudeDifference / 2);
        return 2 * EARTH_RADIUS_METRES * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
    }

    private static HandoverLocation location(ShipmentHandoverCatalog.Location location) {
        return new HandoverLocation(location.label(), location.latitude(), location.longitude());
    }

    private static boolean coordinate(double latitude, double longitude) {
        return Double.isFinite(latitude)
                && latitude >= -90
                && latitude <= 90
                && Double.isFinite(longitude)
                && longitude >= -180
                && longitude <= 180;
    }

    private static String fingerprint(ConfirmHandover command, UUID actor) {
        return hash(String.format(
                Locale.ROOT,
                "%s|%s|%s|%s|%.7f|%.7f|%s|%s",
                actor,
                command.commandId(),
                command.captureMode(),
                command.observedAt(),
                command.latitude(),
                command.longitude(),
                command.quantityOutcome(),
                command.quantityNote()));
    }

    private static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static UUID requiredId(UUID value) {
        if (value == null) {
            throw HandoverException.invalidRequest();
        }
        return value;
    }

    private static String requiredText(String value) {
        if (value == null || value.isBlank()) {
            throw HandoverException.invalidRequest();
        }
        return value.strip();
    }

    private static Instant databaseTime(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    public record IssueChallenge(HandoverType type, UUID deliveryOrderId, UUID counterpartyUserId) {}

    public record IssuedChallenge(HandoverChallenge challenge, String qrPayload) {}

    public record ConfirmHandover(
            UUID commandId,
            String qrPayload,
            CaptureMode captureMode,
            Instant observedAt,
            double latitude,
            double longitude,
            QuantityOutcome quantityOutcome,
            String quantityNote) {}
}
