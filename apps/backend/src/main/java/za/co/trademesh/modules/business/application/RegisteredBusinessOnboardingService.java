package za.co.trademesh.modules.business.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.access.application.BusinessMembershipService;
import za.co.trademesh.modules.business.domain.BusinessLifecycleStatus;
import za.co.trademesh.modules.business.domain.BusinessProfile;
import za.co.trademesh.modules.business.domain.BusinessRepository;
import za.co.trademesh.modules.business.domain.BusinessVerificationStatus;
import za.co.trademesh.modules.business.domain.OnboardingState;
import za.co.trademesh.modules.business.domain.RegisteredBusinessOnboarding;
import za.co.trademesh.modules.business.domain.RegistrationNumber;
import za.co.trademesh.modules.business.events.BusinessEvent;

@Service
public class RegisteredBusinessOnboardingService {

    private final BusinessRepository businesses;
    private final CompanyRegistryProvider companyRegistry;
    private final BusinessMembershipService memberships;
    private final BusinessEventPublisher events;
    private final Clock clock;

    public RegisteredBusinessOnboardingService(
            BusinessRepository businesses,
            CompanyRegistryProvider companyRegistry,
            BusinessMembershipService memberships,
            BusinessEventPublisher events,
            Clock clock) {
        this.businesses = businesses;
        this.companyRegistry = companyRegistry;
        this.memberships = memberships;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public RegisteredBusinessOnboarding start(String rawRegistrationNumber, UUID ownerUserId) {
        RegistrationNumber registrationNumber = normalize(rawRegistrationNumber);

        if (businesses.businessExists(registrationNumber)) {
            throw BusinessException.registrationAlreadyOnboarded();
        }

        var existing = businesses.findOnboardingByRegistrationNumber(registrationNumber);
        if (existing.isPresent()) {
            if (existing.get().belongsTo(ownerUserId)
                    && existing.get().state() == OnboardingState.PENDING_CONFIRMATION) {
                return existing.get();
            }
            throw BusinessException.registrationAlreadyOnboarded();
        }

        CompanyRegistryProvider.RegistryCompany company = companyRegistry
                .findByRegistrationNumber(registrationNumber)
                .orElseThrow(BusinessException::companyNotFound);
        Instant now = clock.instant();
        RegisteredBusinessOnboarding onboarding = new RegisteredBusinessOnboarding(
                UUID.randomUUID(),
                ownerUserId,
                registrationNumber,
                company.legalName(),
                company.tradingName(),
                company.registeredAddress(),
                company.registryReference(),
                OnboardingState.PENDING_CONFIRMATION,
                null,
                now,
                null);

        try {
            businesses.saveOnboarding(onboarding);
        } catch (DataIntegrityViolationException duplicateRegistrationRace) {
            throw BusinessException.registrationAlreadyOnboarded();
        }

        events.publish(new BusinessEvent.OnboardingStarted(
                UUID.randomUUID(), onboarding.id(), ownerUserId, registrationNumber.value(), now));
        return onboarding;
    }

    @Transactional(readOnly = true)
    public RegisteredBusinessOnboarding getOnboarding(UUID onboardingId, UUID userId) {
        RegisteredBusinessOnboarding onboarding =
                businesses.findOnboardingById(onboardingId).orElseThrow(BusinessException::onboardingNotFound);
        requireOwner(onboarding, userId);
        return onboarding;
    }

    @Transactional
    public BusinessProfile confirm(UUID onboardingId, UUID userId) {
        RegisteredBusinessOnboarding onboarding =
                businesses.findOnboardingById(onboardingId).orElseThrow(BusinessException::onboardingNotFound);
        requireOwner(onboarding, userId);

        if (onboarding.state() == OnboardingState.CONFIRMED) {
            return businesses
                    .findBusinessById(onboarding.businessId())
                    .orElseThrow(BusinessException::businessNotFound);
        }
        if (businesses.businessExists(onboarding.registrationNumber())) {
            throw BusinessException.registrationAlreadyOnboarded();
        }

        Instant now = clock.instant();
        BusinessProfile business = new BusinessProfile(
                UUID.randomUUID(),
                onboarding.registrationNumber(),
                onboarding.legalName(),
                onboarding.tradingName(),
                onboarding.registeredAddress(),
                BusinessVerificationStatus.REGISTRY_VERIFIED,
                BusinessLifecycleStatus.ACTIVE,
                userId,
                now);

        try {
            businesses.saveBusiness(business);
            if (!businesses.confirmOnboarding(onboarding.confirm(business.id(), now))) {
                throw BusinessException.onboardingStateChanged();
            }
            memberships.grantOwner(business.id(), userId);
        } catch (DataIntegrityViolationException duplicateRegistrationRace) {
            throw BusinessException.registrationAlreadyOnboarded();
        }

        events.publish(new BusinessEvent.ProfileConfirmed(
                UUID.randomUUID(),
                onboarding.id(),
                business.id(),
                userId,
                business.registrationNumber().value(),
                now));
        return business;
    }

    @Transactional(readOnly = true)
    public BusinessProfile getBusiness(UUID businessId) {
        return businesses.findBusinessById(businessId).orElseThrow(BusinessException::businessNotFound);
    }

    private static RegistrationNumber normalize(String rawRegistrationNumber) {
        try {
            return RegistrationNumber.from(rawRegistrationNumber);
        } catch (IllegalArgumentException invalidRegistrationNumber) {
            throw BusinessException.invalidRegistrationNumber();
        }
    }

    private static void requireOwner(RegisteredBusinessOnboarding onboarding, UUID userId) {
        if (!onboarding.belongsTo(userId)) {
            throw BusinessException.onboardingAccessDenied();
        }
    }
}
