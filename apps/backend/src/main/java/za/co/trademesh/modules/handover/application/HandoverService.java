package za.co.trademesh.modules.handover.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.access.application.ActiveUserDirectory;
import za.co.trademesh.modules.handover.domain.CaptureMode;
import za.co.trademesh.modules.handover.domain.DeliveryDisputeResolution;
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
import za.co.trademesh.modules.procurement.application.DeliveryOrderQuantityCatalog;
import za.co.trademesh.modules.shipment.application.ShipmentHandoverCatalog;
import za.co.trademesh.modules.shipment.application.ShipmentHandoverCatalog.Completion;
import za.co.trademesh.modules.shipment.application.ShipmentHandoverCatalog.DeliveryStop;
import za.co.trademesh.modules.shipment.application.ShipmentHandoverCatalog.HandoverShipment;
import za.co.trademesh.modules.shipment.application.ShipmentHandoverCatalog.Stage;
import za.co.trademesh.shared.events.DomainEvents;

@Service
public class HandoverService implements DeliveryReleaseGate {

    private static final double EARTH_RADIUS_METRES = 6_371_000;
    private static final int MAX_QR_PAYLOAD_LENGTH = 1024;
    private static final int MAX_PHOTO_URL_LENGTH = 2048;

    private final HandoverRepository handovers;
    private final ShipmentHandoverCatalog shipments;
    private final DeliveryOrderQuantityCatalog quantities;
    private final ActiveUserDirectory users;
    private final HandoverTokenGenerator tokens;
    private final HandoverProperties properties;
    private final DomainEvents events;
    private final Clock clock;

    public HandoverService(
            HandoverRepository handovers,
            ShipmentHandoverCatalog shipments,
            DeliveryOrderQuantityCatalog quantities,
            ActiveUserDirectory users,
            HandoverTokenGenerator tokens,
            HandoverProperties properties,
            DomainEvents events,
            Clock clock) {
        this.handovers = handovers;
        this.shipments = shipments;
        this.quantities = quantities;
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
        ExpectedHandover expected = expectedHandover(owner, shipment, command.type(), command.deliveryOrderId());
        Instant now = databaseTime(clock.instant());
        Instant expiresAt = now.plus(properties.challengeTtl());
        handovers.expireActive(shipmentReference, command.type(), command.deliveryOrderId(), now);
        if (handovers
                .findActive(shipmentReference, command.type(), command.deliveryOrderId())
                .isPresent()) {
            throw HandoverException.activeChallengeExists();
        }

        UUID challengeId = UUID.randomUUID();
        String qrPayload = tokens.generate(new HandoverTokenGenerator.TokenClaims(
                challengeId, shipmentReference, expected.quantity(), expected.unitOfMeasure(), expiresAt));
        HandoverChallenge challenge = new HandoverChallenge(
                challengeId,
                shipmentReference,
                owner,
                command.type(),
                command.deliveryOrderId(),
                HandoverState.PENDING,
                hash(qrPayload),
                actor,
                counterparty,
                expected.location(),
                expected.quantity(),
                expected.unitOfMeasure(),
                properties.locationToleranceMetres(),
                expiresAt,
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
        HandoverTokenGenerator.TokenClaims claims = tokens.verify(normalized.qrPayload());
        String nonceHash = hash(normalized.qrPayload());
        HandoverChallenge current =
                handovers.findByNonceHashForUpdate(nonceHash).orElseThrow(HandoverException::invalidToken);
        Instant now = databaseTime(clock.instant());
        validateClaims(current, claims);

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
                null,
                null,
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
                new HandoverEvent.HandoverFinalized(
                        confirmed.id(),
                        confirmed.shipmentId(),
                        confirmed.businessId(),
                        confirmed.type().name(),
                        outcome.name()),
                actor.toString());
        return handovers.findByNonceHashForUpdate(nonceHash).orElseThrow();
    }

    @Transactional(noRollbackFor = HandoverException.class)
    public HandoverChallenge scanDelivery(UUID shipmentId, ScanDelivery command, UUID actorUserId) {
        UUID actor = requiredId(actorUserId);
        UUID shipmentReference = requiredId(shipmentId);
        ScanDelivery normalized = normalize(command);
        HandoverTokenGenerator.TokenClaims claims = tokens.verify(normalized.qrPayload());
        if (!claims.shipmentId().equals(shipmentReference)) {
            throw HandoverException.invalidToken();
        }
        String nonceHash = hash(normalized.qrPayload());
        HandoverChallenge current =
                handovers.findByNonceHashForUpdate(nonceHash).orElseThrow(HandoverException::invalidToken);
        validateClaims(current, claims);
        if (current.type() != HandoverType.DELIVERY || current.expectedQuantity() == null) {
            throw HandoverException.invalidToken();
        }
        Instant now = databaseTime(clock.instant());
        QuantityOutcome quantityOutcome = normalized.capturedQuantity().compareTo(current.expectedQuantity()) == 0
                ? QuantityOutcome.MATCHED
                : QuantityOutcome.DISPUTED;
        String note = quantityOutcome == QuantityOutcome.MATCHED
                ? "Captured quantity matched the expected delivery quantity."
                : "Captured quantity differs from the expected delivery quantity.";
        ConfirmHandover attempt = new ConfirmHandover(
                normalized.commandId(),
                normalized.qrPayload(),
                CaptureMode.ONLINE,
                now,
                normalized.latitude(),
                normalized.longitude(),
                quantityOutcome,
                note);
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
        if (current.state().terminal()) {
            HandoverAttemptOutcome outcome = current.state() == HandoverState.EXPIRED
                    ? HandoverAttemptOutcome.CHALLENGE_EXPIRED
                    : HandoverAttemptOutcome.CHALLENGE_REPLAYED;
            throw reject(
                    current,
                    attempt,
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
                    attempt,
                    actor,
                    HandoverAttemptOutcome.CHALLENGE_EXPIRED,
                    HandoverException.expired(),
                    now);
        }
        if (!current.counterpartyUserId().equals(actor)) {
            throw reject(
                    current,
                    attempt,
                    actor,
                    HandoverAttemptOutcome.PARTICIPANT_MISMATCH,
                    HandoverException.participantMismatch(),
                    now);
        }
        HandoverShipment shipment = shipments
                .findOwned(current.businessId(), current.shipmentId())
                .filter(value -> validState(value, current))
                .orElseThrow(() -> reject(
                        current,
                        attempt,
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
                    attempt,
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
                HandoverParty.COUNTERPARTY,
                now,
                now,
                normalized.latitude(),
                normalized.longitude(),
                distance,
                normalized.capturedQuantity(),
                normalized.photoUrl(),
                quantityOutcome,
                note);
        if (!handovers.saveConfirmation(confirmation)) {
            throw HandoverException.commandConflict();
        }
        saveAttempt(current, attempt, actor, HandoverAttemptOutcome.ACCEPTED, "Delivery scan accepted.", now);
        events.publish(
                new HandoverEvent.ConfirmationAccepted(current.id(), current.shipmentId(), HandoverParty.COUNTERPARTY),
                actor.toString());
        HandoverState outcome =
                quantityOutcome == QuantityOutcome.MATCHED ? HandoverState.COMPLETED : HandoverState.DISPUTED;
        if (!handovers.changeState(current.id(), HandoverState.PENDING, outcome, now)) {
            throw HandoverException.replayed();
        }
        HandoverChallenge finalized =
                handovers.findByNonceHashForUpdate(nonceHash).orElseThrow(HandoverException::notFound);
        completeShipment(finalized, outcome, actor, shipment);
        events.publish(
                new HandoverEvent.HandoverFinalized(
                        finalized.id(),
                        finalized.shipmentId(),
                        finalized.businessId(),
                        finalized.type().name(),
                        outcome.name()),
                actor.toString());
        return finalized;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean releaseAllowed(UUID businessId, UUID shipmentId, List<UUID> orderIds) {
        UUID owner = requiredId(businessId);
        UUID shipmentReference = requiredId(shipmentId);
        if (orderIds == null || orderIds.isEmpty() || orderIds.stream().anyMatch(java.util.Objects::isNull)) {
            return false;
        }
        var latest = latestDeliveryChallenges(owner, shipmentReference, orderIds);
        if (latest.size() != Set.copyOf(orderIds).size()
                || latest.values().stream().anyMatch(challenge -> !finalized(challenge.state()))) {
            return false;
        }
        boolean disputed = latest.values().stream().anyMatch(challenge -> challenge.state() == HandoverState.DISPUTED);
        return !disputed || handovers.findResolution(owner, shipmentReference).isPresent();
    }

    @Override
    @Transactional
    public Resolution resolve(
            UUID businessId, UUID shipmentId, UUID commandId, BigDecimal resolvedAmount, UUID actorUserId) {
        UUID owner = requiredId(businessId);
        UUID shipmentReference = requiredId(shipmentId);
        UUID commandReference = requiredId(commandId);
        UUID actor = requiredId(actorUserId);
        BigDecimal amount = positiveAmount(resolvedAmount);
        String inputFingerprint =
                hash(String.join("|", owner.toString(), shipmentReference.toString(), amount.toPlainString()));
        Optional<DeliveryDisputeResolution> prior = handovers.findResolutionByCommandId(commandReference);
        if (prior.isPresent()) {
            DeliveryDisputeResolution existing = prior.get();
            if (!existing.businessId().equals(owner)
                    || !existing.shipmentId().equals(shipmentReference)
                    || !existing.inputFingerprint().equals(inputFingerprint)) {
                throw HandoverException.commandConflict();
            }
            return new Resolution(existing.id(), existing.resolvedAmount());
        }
        if (handovers.findResolution(owner, shipmentReference).isPresent()) {
            throw HandoverException.commandConflict();
        }
        boolean hasDispute = handovers.findByShipment(shipmentReference).stream()
                .anyMatch(challenge -> challenge.businessId().equals(owner)
                        && challenge.type() == HandoverType.DELIVERY
                        && challenge.state() == HandoverState.DISPUTED);
        if (!hasDispute) {
            throw HandoverException.disputeNotFound();
        }
        Instant now = databaseTime(clock.instant());
        DeliveryDisputeResolution resolution = new DeliveryDisputeResolution(
                UUID.randomUUID(), shipmentReference, owner, commandReference, inputFingerprint, amount, actor, now);
        if (!handovers.saveResolution(resolution)) {
            throw HandoverException.commandConflict();
        }
        events.publish(
                new HandoverEvent.DisputeResolved(resolution.id(), shipmentReference, owner, amount), actor.toString());
        return new Resolution(resolution.id(), resolution.resolvedAmount());
    }

    @Transactional(readOnly = true)
    public DeliveryStatus deliveryStatus(UUID businessId, UUID shipmentId) {
        UUID owner = requiredId(businessId);
        UUID shipmentReference = requiredId(shipmentId);
        shipments.findOwned(owner, shipmentReference).orElseThrow(HandoverException::notFound);
        List<HandoverChallenge> latest = latestDeliveryChallenges(owner, shipmentReference, null).values().stream()
                .sorted(java.util.Comparator.comparing(HandoverChallenge::createdAt))
                .toList();
        if (latest.isEmpty()) {
            throw HandoverException.notFound();
        }
        Optional<DeliveryDisputeResolution> resolution = handovers.findResolution(owner, shipmentReference);
        String status;
        if (latest.stream().anyMatch(challenge -> challenge.state() == HandoverState.DISPUTED)) {
            status = resolution.isPresent() ? "RESOLVED" : "DISPUTED";
        } else if (latest.stream().allMatch(challenge -> challenge.state() == HandoverState.COMPLETED)) {
            status = "CLEAN";
        } else if (latest.stream().anyMatch(challenge -> challenge.state() == HandoverState.PENDING)) {
            status = "PENDING";
        } else {
            status = "EXPIRED";
        }
        Instant updatedAt = resolution
                .map(DeliveryDisputeResolution::resolvedAt)
                .orElseGet(() -> latest.stream()
                        .map(challenge ->
                                challenge.completedAt() == null ? challenge.createdAt() : challenge.completedAt())
                        .max(Instant::compareTo)
                        .orElseThrow());
        return new DeliveryStatus(
                shipmentReference,
                owner,
                status,
                latest.stream().map(HandoverService::deliveryCheck).toList(),
                resolution.map(DeliveryDisputeResolution::resolvedAmount).orElse(null),
                updatedAt);
    }

    private ExpectedHandover expectedHandover(
            UUID businessId, HandoverShipment shipment, HandoverType type, UUID deliveryOrderId) {
        if (type == HandoverType.COLLECTION) {
            if (deliveryOrderId != null
                    || !shipment.businessId().equals(businessId)
                    || shipment.stage() != Stage.AWAITING_COLLECTION) {
                throw HandoverException.stateConflict();
            }
            return new ExpectedHandover(location(shipment.collectionLocation()), null, null);
        }
        if (deliveryOrderId == null || (shipment.stage() != Stage.IN_TRANSIT && shipment.stage() != Stage.DELAYED)) {
            throw HandoverException.stateConflict();
        }
        HandoverLocation deliveryLocation = shipment.deliveryStops().stream()
                .filter(stop -> stop.orderId().equals(deliveryOrderId))
                .filter(stop -> stop.buyerBusinessId().equals(businessId))
                .findFirst()
                .map(DeliveryStop::location)
                .map(HandoverService::location)
                .orElseThrow(HandoverException::invalidRequest);
        var expected = quantities
                .findExpectedQuantity(businessId, deliveryOrderId)
                .orElseThrow(HandoverException::quantityUnavailable);
        return new ExpectedHandover(deliveryLocation, expected.quantity(), expected.unitOfMeasure());
    }

    private void completeShipment(
            HandoverChallenge challenge, HandoverState outcome, UUID actor, HandoverShipment shipment) {
        if (outcome == HandoverState.DISPUTED && challenge.type() == HandoverType.COLLECTION) {
            return;
        }
        if (challenge.type() == HandoverType.DELIVERY) {
            if (!handovers
                    .findFinalizedDeliveryOrderIds(challenge.shipmentId())
                    .containsAll(shipment.deliveryStops().stream()
                            .map(DeliveryStop::orderId)
                            .toList())) {
                return;
            }
            outcome = handovers.findByShipment(challenge.shipmentId()).stream()
                            .anyMatch(value ->
                                    value.type() == HandoverType.DELIVERY && value.state() == HandoverState.DISPUTED)
                    ? HandoverState.DISPUTED
                    : HandoverState.COMPLETED;
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
        if (qrPayload.length() > MAX_QR_PAYLOAD_LENGTH) {
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

    private ScanDelivery normalize(ScanDelivery command) {
        if (command == null || command.commandId() == null || !coordinate(command.latitude(), command.longitude())) {
            throw HandoverException.invalidRequest();
        }
        String qrPayload = requiredText(command.qrPayload());
        if (qrPayload.length() > MAX_QR_PAYLOAD_LENGTH) {
            throw HandoverException.invalidRequest();
        }
        BigDecimal capturedQuantity = decimal(command.capturedQuantity(), false);
        return new ScanDelivery(
                command.commandId(),
                qrPayload,
                capturedQuantity,
                photoUrl(command.photoUrl()),
                command.latitude(),
                command.longitude());
    }

    private static void validateClaims(HandoverChallenge challenge, HandoverTokenGenerator.TokenClaims claims) {
        boolean quantityMatches = challenge.expectedQuantity() == null
                ? claims.expectedQuantity() == null
                : claims.expectedQuantity() != null
                        && challenge.expectedQuantity().compareTo(claims.expectedQuantity()) == 0;
        if (!challenge.id().equals(claims.challengeId())
                || !challenge.shipmentId().equals(claims.shipmentId())
                || !quantityMatches
                || !java.util.Objects.equals(challenge.unitOfMeasure(), claims.unitOfMeasure())
                || challenge.expiresAt().toEpochMilli() != claims.expiresAt().toEpochMilli()) {
            throw HandoverException.invalidToken();
        }
    }

    private Map<UUID, HandoverChallenge> latestDeliveryChallenges(
            UUID businessId, UUID shipmentId, List<UUID> orderIds) {
        Set<UUID> requested = orderIds == null ? null : Set.copyOf(orderIds);
        Map<UUID, HandoverChallenge> latest = new HashMap<>();
        handovers.findByShipment(shipmentId).stream()
                .filter(challenge -> challenge.type() == HandoverType.DELIVERY)
                .filter(challenge -> challenge.businessId().equals(businessId))
                .filter(challenge -> requested == null || requested.contains(challenge.deliveryOrderId()))
                .forEach(challenge -> latest.merge(
                        challenge.deliveryOrderId(),
                        challenge,
                        (first, second) -> first.createdAt().isAfter(second.createdAt()) ? first : second));
        return Map.copyOf(latest);
    }

    private static boolean finalized(HandoverState state) {
        return state == HandoverState.COMPLETED || state == HandoverState.DISPUTED;
    }

    private static DeliveryCheck deliveryCheck(HandoverChallenge challenge) {
        HandoverConfirmation captured = challenge.confirmations().stream()
                .filter(confirmation -> confirmation.capturedQuantity() != null)
                .reduce((first, second) -> second)
                .orElse(null);
        return new DeliveryCheck(
                challenge.id(),
                challenge.deliveryOrderId(),
                challenge.state(),
                challenge.expectedQuantity(),
                captured == null ? null : captured.capturedQuantity(),
                challenge.unitOfMeasure(),
                captured == null ? null : captured.photoUrl(),
                challenge.completedAt());
    }

    private static BigDecimal positiveAmount(BigDecimal amount) {
        BigDecimal normalized = decimal(amount, true);
        if (normalized.signum() <= 0) {
            throw HandoverException.invalidRequest();
        }
        return normalized;
    }

    private static BigDecimal decimal(BigDecimal amount, boolean positive) {
        if (amount == null) {
            throw HandoverException.invalidRequest();
        }
        try {
            BigDecimal normalized = amount.setScale(4, RoundingMode.UNNECESSARY);
            if (normalized.precision() > 19 || (positive ? normalized.signum() <= 0 : normalized.signum() < 0)) {
                throw HandoverException.invalidRequest();
            }
            return normalized;
        } catch (ArithmeticException invalidScale) {
            throw HandoverException.invalidRequest();
        }
    }

    private static String photoUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > MAX_PHOTO_URL_LENGTH) {
            throw HandoverException.invalidRequest();
        }
        try {
            URI uri = URI.create(normalized);
            if (uri.getHost() == null
                    || uri.getUserInfo() != null
                    || (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))) {
                throw HandoverException.invalidRequest();
            }
            return normalized;
        } catch (IllegalArgumentException invalidUri) {
            throw HandoverException.invalidRequest();
        }
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

    private static String fingerprint(ScanDelivery command, UUID actor) {
        return hash(String.format(
                Locale.ROOT,
                "%s|%s|%s|%s|%.7f|%.7f",
                actor,
                command.commandId(),
                command.capturedQuantity().toPlainString(),
                command.photoUrl(),
                command.latitude(),
                command.longitude()));
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

    public record ScanDelivery(
            UUID commandId,
            String qrPayload,
            BigDecimal capturedQuantity,
            String photoUrl,
            double latitude,
            double longitude) {}

    public record DeliveryStatus(
            UUID shipmentId,
            UUID businessId,
            String verificationStatus,
            List<DeliveryCheck> deliveries,
            BigDecimal resolvedAmount,
            Instant updatedAt) {
        public DeliveryStatus {
            deliveries = List.copyOf(deliveries);
        }
    }

    public record DeliveryCheck(
            UUID challengeId,
            UUID deliveryOrderId,
            HandoverState state,
            BigDecimal expectedQuantity,
            BigDecimal capturedQuantity,
            String unitOfMeasure,
            String photoUrl,
            Instant completedAt) {}

    private record ExpectedHandover(HandoverLocation location, BigDecimal quantity, String unitOfMeasure) {}
}
