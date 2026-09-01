package za.co.trademesh.modules.business.api;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;
import za.co.trademesh.modules.business.domain.BusinessLifecycleStatus;
import za.co.trademesh.modules.business.domain.BusinessProfile;
import za.co.trademesh.modules.business.domain.BusinessVerificationStatus;
import za.co.trademesh.modules.business.domain.OnboardingState;
import za.co.trademesh.modules.business.domain.RegisteredBusinessOnboarding;

public final class BusinessContracts {

    private BusinessContracts() {}

    public record StartRegisteredOnboardingRequest(@NotBlank String registrationNumber) {}

    public record RegisteredOnboardingResponse(
            UUID onboardingId,
            String registrationNumber,
            String legalName,
            String tradingName,
            String registeredAddress,
            OnboardingState state,
            boolean trusted,
            UUID businessId,
            Instant createdAt,
            Instant confirmedAt) {

        static RegisteredOnboardingResponse from(RegisteredBusinessOnboarding onboarding) {
            return new RegisteredOnboardingResponse(
                    onboarding.id(),
                    onboarding.registrationNumber().value(),
                    onboarding.legalName(),
                    onboarding.tradingName(),
                    onboarding.registeredAddress(),
                    onboarding.state(),
                    onboarding.state() == OnboardingState.CONFIRMED,
                    onboarding.businessId(),
                    onboarding.createdAt(),
                    onboarding.confirmedAt());
        }
    }

    public record BusinessProfileResponse(
            UUID businessId,
            String registrationNumber,
            String legalName,
            String tradingName,
            String registeredAddress,
            BusinessVerificationStatus verificationStatus,
            BusinessLifecycleStatus lifecycleStatus,
            boolean trusted,
            Instant createdAt) {

        static BusinessProfileResponse from(BusinessProfile business) {
            return new BusinessProfileResponse(
                    business.id(),
                    business.registrationNumber().value(),
                    business.legalName(),
                    business.tradingName(),
                    business.registeredAddress(),
                    business.verificationStatus(),
                    business.lifecycleStatus(),
                    true,
                    business.createdAt());
        }
    }
}
