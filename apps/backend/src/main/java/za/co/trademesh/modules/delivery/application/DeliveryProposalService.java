package za.co.trademesh.modules.delivery.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.delivery.domain.DeliveryMobileChannel;
import za.co.trademesh.modules.delivery.domain.DeliveryProposal;
import za.co.trademesh.modules.delivery.domain.DeliveryProposalRepository;
import za.co.trademesh.modules.delivery.domain.DeliveryProposalStatus;
import za.co.trademesh.modules.delivery.events.DeliveryEvent;
import za.co.trademesh.modules.notification.application.MobileNotificationRequests;
import za.co.trademesh.modules.notification.application.NotificationRequests;
import za.co.trademesh.modules.notification.application.NotificationTemplates;
import za.co.trademesh.modules.shipment.application.ShipmentAccessCatalog;
import za.co.trademesh.shared.events.DomainEvents;

@Service
public class DeliveryProposalService {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");

    private final ShipmentAccessCatalog shipments;
    private final DeliveryProposalRepository proposals;
    private final DeliveryConfirmationTokens tokens;
    private final DeliveryConfirmationProperties properties;
    private final NotificationRequests emailNotifications;
    private final MobileNotificationRequests mobileNotifications;
    private final DomainEvents events;
    private final Clock clock;

    public DeliveryProposalService(
            ShipmentAccessCatalog shipments,
            DeliveryProposalRepository proposals,
            DeliveryConfirmationTokens tokens,
            DeliveryConfirmationProperties properties,
            NotificationRequests emailNotifications,
            MobileNotificationRequests mobileNotifications,
            DomainEvents events,
            Clock clock) {
        this.shipments = shipments;
        this.proposals = proposals;
        this.tokens = tokens;
        this.properties = properties;
        this.emailNotifications = emailNotifications;
        this.mobileNotifications = mobileNotifications;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public CreatedProposal propose(UUID businessId, UUID shipmentId, ProposeDelivery command, UUID actorUserId) {
        UUID owner = requiredId(businessId);
        UUID shipment = requiredId(shipmentId);
        UUID actor = requiredId(actorUserId);
        ProposeDelivery normalized = normalize(command);
        shipments
                .findOwned(owner, shipment)
                .filter(ShipmentAccessCatalog.ShipmentAccess::acceptsTelemetry)
                .orElseThrow(DeliveryException::shipmentNotFound);
        String fingerprint = fingerprint(
                shipment, normalized.recipientEmail(), normalized.recipientPhone(), normalized.mobileChannel());
        Optional<DeliveryProposal> existing = proposals.findByRequest(owner, normalized.requestId());
        if (existing.isEmpty()) {
            existing = proposals.findByShipment(owner, shipment);
        }
        if (existing.isPresent()) {
            return sameProposal(existing.get(), fingerprint);
        }

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        DeliveryConfirmationTokens.IssuedToken token = tokens.issue();
        DeliveryProposal proposal = new DeliveryProposal(
                UUID.randomUUID(),
                owner,
                shipment,
                normalized.requestId(),
                fingerprint,
                normalized.recipientEmail(),
                normalized.recipientPhone(),
                normalized.mobileChannel(),
                DeliveryProposalStatus.PROPOSED,
                now.plus(properties.timeToLive()),
                now,
                null);
        if (!proposals.save(proposal, token.hash())) {
            DeliveryProposal raced = proposals
                    .findByRequest(owner, normalized.requestId())
                    .or(() -> proposals.findByShipment(owner, shipment))
                    .orElseThrow(DeliveryException::proposalConflict);
            return sameProposal(raced, fingerprint);
        }

        String confirmationUrl = properties.confirmationBaseUrl() + "/" + token.rawToken();
        emailNotifications.requestEmail(new NotificationRequests.EmailRequest(
                "delivery-confirmation-email:" + proposal.id(),
                proposal.recipientEmail(),
                null,
                "SHIPMENT_UPDATE",
                NotificationTemplates.DELIVERY_CONFIRMATION,
                NotificationTemplates.DELIVERY_CONFIRMATION_VERSION,
                Map.of("confirmationUrl", confirmationUrl),
                true));
        mobileNotifications.requestMobile(new MobileNotificationRequests.MobileRequest(
                "delivery-confirmation-mobile:" + proposal.id(),
                proposal.recipientPhone(),
                MobileNotificationRequests.MobileChannel.valueOf(
                        proposal.mobileChannel().name()),
                "A delivery is waiting for your confirmation: " + confirmationUrl));
        events.publish(new DeliveryEvent.ProposalCreated(proposal.id(), shipment, owner), actor.toString());
        return new CreatedProposal(proposal, true);
    }

    @Transactional(readOnly = true)
    public DeliveryProposal preview(String rawToken) {
        DeliveryProposal proposal = proposals
                .findByTokenHash(tokens.hash(rawToken))
                .orElseThrow(DeliveryException::confirmationUnavailable);
        if (proposal.isExpiredAt(clock.instant())) {
            throw DeliveryException.confirmationUnavailable();
        }
        return proposal;
    }

    @Transactional(noRollbackFor = DeliveryException.class)
    public DeliveryProposal confirm(String rawToken) {
        String tokenHash = tokens.hash(rawToken);
        DeliveryProposal proposal =
                proposals.findByTokenHash(tokenHash).orElseThrow(DeliveryException::confirmationUnavailable);
        if (proposal.status() == DeliveryProposalStatus.ACCEPTED) {
            return proposal;
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (proposal.isExpiredAt(now)) {
            proposals.expire(proposal.id(), now);
            throw DeliveryException.confirmationUnavailable();
        }
        if (!proposals.accept(proposal.id(), tokenHash, now)) {
            DeliveryProposal current =
                    proposals.findByTokenHash(tokenHash).orElseThrow(DeliveryException::confirmationUnavailable);
            if (current.status() == DeliveryProposalStatus.ACCEPTED) {
                return current;
            }
            throw DeliveryException.confirmationUnavailable();
        }
        DeliveryProposal accepted =
                proposals.findByTokenHash(tokenHash).orElseThrow(DeliveryException::confirmationUnavailable);
        events.publish(
                new DeliveryEvent.DeliveryAccepted(accepted.id(), accepted.shipmentId(), accepted.businessId()),
                "delivery-confirmation-link");
        return accepted;
    }

    private static CreatedProposal sameProposal(DeliveryProposal existing, String fingerprint) {
        if (!existing.inputFingerprint().equals(fingerprint)) {
            throw DeliveryException.proposalConflict();
        }
        return new CreatedProposal(existing, false);
    }

    private static ProposeDelivery normalize(ProposeDelivery command) {
        if (command == null || command.mobileChannel() == null) {
            throw DeliveryException.invalidProposal();
        }
        String email = command.recipientEmail() == null
                ? ""
                : command.recipientEmail().strip().toLowerCase(java.util.Locale.ROOT);
        String phone =
                command.recipientPhone() == null ? "" : command.recipientPhone().strip();
        if (!EMAIL.matcher(email).matches()
                || email.length() > 320
                || !E164.matcher(phone).matches()) {
            throw DeliveryException.invalidProposal();
        }
        return new ProposeDelivery(requiredId(command.requestId()), email, phone, command.mobileChannel());
    }

    private static UUID requiredId(UUID value) {
        if (value == null) {
            throw DeliveryException.invalidProposal();
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
            throw new IllegalStateException("SHA-256 must be available", impossible);
        }
    }

    public record ProposeDelivery(
            UUID requestId, String recipientEmail, String recipientPhone, DeliveryMobileChannel mobileChannel) {}

    public record CreatedProposal(DeliveryProposal proposal, boolean newlyCreated) {}
}
