package za.co.trademesh.modules.business.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import za.co.trademesh.modules.access.application.AuthService;
import za.co.trademesh.modules.access.domain.RegistrationType;
import za.co.trademesh.modules.business.domain.BusinessVerificationStatus;
import za.co.trademesh.modules.business.domain.OnboardingState;
import za.co.trademesh.modules.business.events.BusinessEvent;
import za.co.trademesh.support.PostgresIntegrationTest;

@RecordApplicationEvents
class RegisteredBusinessOnboardingServiceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private RegisteredBusinessOnboardingService onboardingService;

    @Autowired
    private AuthService authService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEvents applicationEvents;

    @BeforeEach
    @AfterEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM access_refresh_session");
        jdbcTemplate.update("DELETE FROM access_business_membership");
        jdbcTemplate.update("DELETE FROM business_registered_onboarding");
        jdbcTemplate.update("DELETE FROM business_profile");
        jdbcTemplate.update("DELETE FROM access_user_role");
        jdbcTemplate.update("DELETE FROM access_user_account");
    }

    @Test
    void keepsRegistryDataUntrustedUntilTheOwnerConfirmsIt() {
        UUID ownerId = registerOwner("owner@example.com");

        var onboarding = onboardingService.start(" 2024-123456-07 ", ownerId);

        assertThat(onboarding.registrationNumber().value()).isEqualTo("2024/123456/07");
        assertThat(onboarding.legalName()).isEqualTo("Mahlako General Trading (Pty) Ltd");
        assertThat(onboarding.state()).isEqualTo(OnboardingState.PENDING_CONFIRMATION);
        assertThat(onboarding.businessId()).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM business_profile", Integer.class))
                .isZero();
        assertThat(applicationEvents.stream(BusinessEvent.OnboardingStarted.class))
                .hasSize(1);

        var business = onboardingService.confirm(onboarding.id(), ownerId);

        assertThat(business.verificationStatus()).isEqualTo(BusinessVerificationStatus.REGISTRY_VERIFIED);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT membership_status FROM access_business_membership WHERE business_id = ? AND user_id = ?",
                        String.class,
                        business.id(),
                        ownerId))
                .isEqualTo("ACTIVE");
        assertThat(applicationEvents.stream(BusinessEvent.ProfileConfirmed.class))
                .hasSize(1);
    }

    @Test
    void confirmationIsIdempotentAndDoesNotPublishASecondStateChange() {
        UUID ownerId = registerOwner("owner@example.com");
        var onboarding = onboardingService.start("2024/123456/07", ownerId);

        var first = onboardingService.confirm(onboarding.id(), ownerId);
        var second = onboardingService.confirm(onboarding.id(), ownerId);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(applicationEvents.stream(BusinessEvent.ProfileConfirmed.class))
                .hasSize(1);
    }

    @Test
    void normalizedRegistrationNumberPreventsDuplicateOnboardingAcrossOwners() {
        UUID firstOwner = registerOwner("first@example.com");
        UUID secondOwner = registerOwner("second@example.com");
        onboardingService.start("2024/123456/07", firstOwner);

        assertThatThrownBy(() -> onboardingService.start("2024-123456-07", secondOwner))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo("REGISTRATION_ALREADY_ONBOARDED");
    }

    @Test
    void anotherAccountCannotReadOrConfirmTheDraft() {
        UUID ownerId = registerOwner("owner@example.com");
        UUID attackerId = registerOwner("attacker@example.com");
        var onboarding = onboardingService.start("2024/123456/07", ownerId);

        assertThatThrownBy(() -> onboardingService.getOnboarding(onboarding.id(), attackerId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo("ONBOARDING_ACCESS_DENIED");
        assertThatThrownBy(() -> onboardingService.confirm(onboarding.id(), attackerId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).code())
                .isEqualTo("ONBOARDING_ACCESS_DENIED");
    }

    private UUID registerOwner(String email) {
        return authService
                .register(email, "correct-horse-battery", RegistrationType.BUSINESS_OWNER)
                .userId();
    }
}
