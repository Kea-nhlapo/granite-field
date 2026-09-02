package za.co.trademesh.modules.business.events;

import java.util.UUID;
import za.co.trademesh.shared.events.DomainEvent;

public sealed interface BusinessEvent extends DomainEvent
        permits BusinessEvent.OnboardingStarted, BusinessEvent.ProfileConfirmed {

    @Override
    default int schemaVersion() {
        return 1;
    }

    record OnboardingStarted(UUID onboardingId, String registrationNumber) implements BusinessEvent {
        @Override
        public String type() {
            return "business.registered-onboarding-started";
        }
    }

    record ProfileConfirmed(UUID onboardingId, UUID businessId, String registrationNumber) implements BusinessEvent {
        @Override
        public String type() {
            return "business.profile-confirmed";
        }
    }
}
