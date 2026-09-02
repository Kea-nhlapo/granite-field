package za.co.trademesh.modules.supplier.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.access.application.AccountIdentityService;
import za.co.trademesh.modules.notification.application.NotificationRequests;
import za.co.trademesh.modules.notification.application.NotificationTemplates;
import za.co.trademesh.modules.supplier.domain.SupplierEmail;
import za.co.trademesh.modules.supplier.domain.SupplierInvitation;
import za.co.trademesh.modules.supplier.domain.SupplierInvitationPurpose;
import za.co.trademesh.modules.supplier.domain.SupplierInvitationRepository;
import za.co.trademesh.modules.supplier.domain.SupplierInvitationStatus;
import za.co.trademesh.modules.supplier.domain.SupplierProfile;
import za.co.trademesh.modules.supplier.domain.SupplierProfileStatus;
import za.co.trademesh.modules.supplier.events.SupplierEvent;
import za.co.trademesh.shared.events.DomainEvents;

@Service
public class SupplierInvitationService {

    private final SupplierInvitationRepository repository;
    private final SupplierInvitationTokens tokens;
    private final SupplierInvitationRateLimiter rateLimiter;
    private final SupplierInvitationProperties properties;
    private final AccountIdentityService accountIdentities;
    private final NotificationRequests notifications;
    private final DomainEvents events;
    private final Clock clock;

    public SupplierInvitationService(
            SupplierInvitationRepository repository,
            SupplierInvitationTokens tokens,
            SupplierInvitationRateLimiter rateLimiter,
            SupplierInvitationProperties properties,
            AccountIdentityService accountIdentities,
            NotificationRequests notifications,
            DomainEvents events,
            Clock clock) {
        this.repository = repository;
        this.tokens = tokens;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.accountIdentities = accountIdentities;
        this.notifications = notifications;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public CreatedInvitation invite(UUID buyerBusinessId, UUID requestId, String rawSupplierEmail, UUID actorUserId) {
        SupplierEmail supplierEmail = normalize(rawSupplierEmail);
        Instant now = clock.instant();
        SupplierProfile profile = repository.getOrCreateTemporaryProfile(supplierEmail, UUID.randomUUID(), now);

        repository.expirePendingForScope(buyerBusinessId, requestId, profile.id(), now);
        if (repository.activeInvitationExists(buyerBusinessId, requestId, profile.id(), now)) {
            throw SupplierException.invitationAlreadyActive();
        }

        SupplierInvitationTokens.IssuedToken issuedToken = tokens.issue();
        SupplierInvitation invitation = new SupplierInvitation(
                UUID.randomUUID(),
                buyerBusinessId,
                profile.id(),
                requestId,
                SupplierInvitationPurpose.QUOTE_RESPONSE,
                SupplierInvitationStatus.PENDING,
                now.plus(properties.timeToLive()),
                null,
                now,
                null,
                null);

        try {
            repository.saveInvitation(invitation, issuedToken.hash());
        } catch (DataIntegrityViolationException concurrentInvitation) {
            throw SupplierException.invitationAlreadyActive();
        }

        notifications.requestEmail(new NotificationRequests.EmailRequest(
                "supplier-invitation:" + invitation.id(),
                profile.email().value(),
                null,
                "SUPPLIER_INVITATION",
                NotificationTemplates.SUPPLIER_INVITATION,
                NotificationTemplates.SUPPLIER_INVITATION_VERSION,
                Map.of("invitationUrl", properties.guestBaseUrl() + "/" + issuedToken.rawToken()),
                true));

        events.publish(
                new SupplierEvent.InvitationCreated(invitation.id(), buyerBusinessId, profile.id(), requestId),
                actorUserId.toString());
        return new CreatedInvitation(invitation, profile, issuedToken.rawToken());
    }

    @Transactional(noRollbackFor = SupplierException.class)
    public GuestInvitation viewGuestInvitation(String rawToken, String clientKey) {
        Instant now = checkRateLimit(clientKey);
        SupplierInvitation invitation = requireAvailable(rawToken, now);
        return new GuestInvitation(invitation);
    }

    @Transactional(noRollbackFor = SupplierException.class)
    public SupplierInvitation submitResponse(
            String rawToken, UUID requestId, UUID responseReference, String clientKey) {
        Instant now = checkRateLimit(clientKey);
        String tokenHash = tokens.hash(rawToken);
        SupplierInvitation invitation =
                repository.findInvitationByTokenHash(tokenHash).orElseThrow(SupplierException::invitationUnavailable);

        if (invitation.isIdempotentResponse(requestId, responseReference)) {
            return invitation;
        }
        if (!invitation.isAvailableAt(now) || !invitation.requestId().equals(requestId)) {
            markExpiredIfNeeded(invitation, now);
            throw SupplierException.invitationUnavailable();
        }

        if (!repository.recordResponse(invitation.id(), tokenHash, requestId, responseReference, now)) {
            SupplierInvitation current = repository
                    .findInvitationByTokenHash(tokenHash)
                    .orElseThrow(SupplierException::invitationUnavailable);
            if (current.isIdempotentResponse(requestId, responseReference)) {
                return current;
            }
            throw SupplierException.invitationStateConflict();
        }

        SupplierInvitation responded =
                repository.findInvitationByTokenHash(tokenHash).orElseThrow(SupplierException::invitationStateConflict);
        events.publish(new SupplierEvent.ResponseRecorded(
                responded.id(), responded.supplierProfileId(), requestId, responseReference));
        return responded;
    }

    @Transactional
    public SupplierInvitation revoke(UUID invitationId, UUID buyerBusinessId, UUID actorUserId) {
        SupplierInvitation invitation = repository
                .findInvitationById(invitationId)
                .filter(candidate -> candidate.buyerBusinessId().equals(buyerBusinessId))
                .orElseThrow(SupplierException::invitationUnavailable);

        if (invitation.status() == SupplierInvitationStatus.REVOKED) {
            return invitation;
        }
        if (invitation.status() != SupplierInvitationStatus.PENDING
                || !repository.revoke(invitationId, buyerBusinessId, clock.instant())) {
            throw SupplierException.invitationStateConflict();
        }

        SupplierInvitation revoked =
                repository.findInvitationById(invitationId).orElseThrow(SupplierException::invitationStateConflict);
        events.publish(new SupplierEvent.InvitationRevoked(invitationId, buyerBusinessId), actorUserId.toString());
        return revoked;
    }

    @Transactional
    public SupplierProfile convert(
            UUID profileId, UUID userId, UUID businessId, String rawInvitationToken, String clientKey) {
        Instant now = checkRateLimit(clientKey);
        SupplierProfile profile = repository.findProfileById(profileId).orElseThrow(SupplierException::profileNotFound);
        AccountIdentityService.AccountIdentity account =
                accountIdentities.findEnabled(userId).orElseThrow(SupplierException::controlNotProven);

        repository
                .findInvitationByTokenHash(tokens.hash(rawInvitationToken))
                .filter(invitation -> invitation.supplierProfileId().equals(profileId))
                .filter(invitation -> invitation.supportsConversionAt(now))
                .orElseThrow(SupplierException::controlNotProven);
        if (account.normalizedEmail().filter(profile.email().value()::equals).isEmpty()) {
            throw SupplierException.controlNotProven();
        }
        if (profile.isClaimedBy(userId, businessId)) {
            return profile;
        }
        if (profile.status() != SupplierProfileStatus.TEMPORARY) {
            throw SupplierException.profileAlreadyClaimed();
        }

        try {
            if (!repository.claimProfile(profileId, userId, businessId, now)) {
                throw SupplierException.profileAlreadyClaimed();
            }
        } catch (DataIntegrityViolationException duplicateClaim) {
            throw SupplierException.businessAlreadyClaimed();
        }

        SupplierProfile converted =
                repository.findProfileById(profileId).orElseThrow(SupplierException::profileNotFound);
        events.publish(new SupplierEvent.ProfileConverted(profileId, userId, businessId), userId.toString());
        return converted;
    }

    private SupplierInvitation requireAvailable(String rawToken, Instant now) {
        SupplierInvitation invitation = repository
                .findInvitationByTokenHash(tokens.hash(rawToken))
                .orElseThrow(SupplierException::invitationUnavailable);
        if (!invitation.isAvailableAt(now)) {
            markExpiredIfNeeded(invitation, now);
            throw SupplierException.invitationUnavailable();
        }
        return invitation;
    }

    private Instant checkRateLimit(String clientKey) {
        Instant now = clock.instant();
        if (!rateLimiter.allow(clientKey, now)) {
            throw SupplierException.invitationRateLimited();
        }
        return now;
    }

    private void markExpiredIfNeeded(SupplierInvitation invitation, Instant now) {
        if (invitation.status() == SupplierInvitationStatus.PENDING
                && !invitation.expiresAt().isAfter(now)) {
            repository.markExpired(invitation.id(), now);
        }
    }

    private static SupplierEmail normalize(String rawSupplierEmail) {
        try {
            return SupplierEmail.from(rawSupplierEmail);
        } catch (IllegalArgumentException invalidEmail) {
            throw SupplierException.invalidEmail();
        }
    }

    public record CreatedInvitation(SupplierInvitation invitation, SupplierProfile profile, String rawToken) {}

    public record GuestInvitation(SupplierInvitation invitation) {}
}
