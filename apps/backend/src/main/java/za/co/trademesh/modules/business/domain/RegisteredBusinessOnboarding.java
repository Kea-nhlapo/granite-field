package za.co.trademesh.modules.business.domain;

import java.time.Instant;
import java.util.UUID;

public record RegisteredBusinessOnboarding(
        UUID id,
        UUID ownerUserId,
        RegistrationNumber registrationNumber,
        String legalName,
        String tradingName,
        String registeredAddress,
        String registryReference,
        OnboardingState state,
        UUID businessId,
        Instant createdAt,
        Instant confirmedAt) {

    public RegisteredBusinessOnboarding {
        if (state == OnboardingState.PENDING_CONFIRMATION && (businessId != null || confirmedAt != null)) {
            throw new IllegalArgumentException("A pending onboarding cannot reference a confirmed business");
        }
        if (state == OnboardingState.CONFIRMED && (businessId == null || confirmedAt == null)) {
            throw new IllegalArgumentException("A confirmed onboarding must reference its business");
        }
    }

    public boolean belongsTo(UUID userId) {
        return ownerUserId.equals(userId);
    }

    public RegisteredBusinessOnboarding confirm(UUID confirmedBusinessId, Instant confirmationTime) {
        return new RegisteredBusinessOnboarding(
                id,
                ownerUserId,
                registrationNumber,
                legalName,
                tradingName,
                registeredAddress,
                registryReference,
                OnboardingState.CONFIRMED,
                confirmedBusinessId,
                createdAt,
                confirmationTime);
    }
}
