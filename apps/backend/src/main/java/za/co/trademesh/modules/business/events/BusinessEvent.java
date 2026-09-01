package za.co.trademesh.modules.business.events;

import java.time.Instant;
import java.util.UUID;

public sealed interface BusinessEvent permits BusinessEvent.OnboardingStarted, BusinessEvent.ProfileConfirmed {

    UUID eventId();

    UUID actorUserId();

    String registrationNumber();

    Instant occurredAt();

    record OnboardingStarted(
            UUID eventId, UUID onboardingId, UUID actorUserId, String registrationNumber, Instant occurredAt)
            implements BusinessEvent {}

    record ProfileConfirmed(
            UUID eventId,
            UUID onboardingId,
            UUID businessId,
            UUID actorUserId,
            String registrationNumber,
            Instant occurredAt)
            implements BusinessEvent {}
}
