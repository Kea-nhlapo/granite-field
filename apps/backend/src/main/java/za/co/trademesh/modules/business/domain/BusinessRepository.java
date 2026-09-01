package za.co.trademesh.modules.business.domain;

import java.util.Optional;
import java.util.UUID;

public interface BusinessRepository {
    Optional<RegisteredBusinessOnboarding> findOnboardingById(UUID onboardingId);

    Optional<RegisteredBusinessOnboarding> findOnboardingByRegistrationNumber(RegistrationNumber registrationNumber);

    Optional<BusinessProfile> findBusinessById(UUID businessId);

    boolean businessExists(RegistrationNumber registrationNumber);

    void saveOnboarding(RegisteredBusinessOnboarding onboarding);

    void saveBusiness(BusinessProfile business);

    boolean confirmOnboarding(RegisteredBusinessOnboarding confirmedOnboarding);
}
