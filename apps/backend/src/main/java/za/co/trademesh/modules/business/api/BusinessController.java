package za.co.trademesh.modules.business.api;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.trademesh.modules.business.application.RegisteredBusinessOnboardingService;
import za.co.trademesh.shared.security.AuthorizationService;

@RestController
@RequestMapping("/api/businesses")
public class BusinessController {

    private final RegisteredBusinessOnboardingService onboardingService;
    private final AuthorizationService authorizationService;

    public BusinessController(
            RegisteredBusinessOnboardingService onboardingService, AuthorizationService authorizationService) {
        this.onboardingService = onboardingService;
        this.authorizationService = authorizationService;
    }

    @PostMapping("/onboarding/registered")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'TRANSPORTER')")
    ResponseEntity<BusinessContracts.RegisteredOnboardingResponse> startRegisteredOnboarding(
            @Valid @RequestBody BusinessContracts.StartRegisteredOnboardingRequest request,
            Authentication authentication) {
        UUID userId = authorizationService.authenticatedUserId(authentication);
        var onboarding = onboardingService.start(request.registrationNumber(), userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BusinessContracts.RegisteredOnboardingResponse.from(onboarding));
    }

    @GetMapping("/onboarding/registered/{onboardingId}")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'TRANSPORTER')")
    BusinessContracts.RegisteredOnboardingResponse getRegisteredOnboarding(
            @PathVariable UUID onboardingId, Authentication authentication) {
        UUID userId = authorizationService.authenticatedUserId(authentication);
        return BusinessContracts.RegisteredOnboardingResponse.from(
                onboardingService.getOnboarding(onboardingId, userId));
    }

    @PostMapping("/onboarding/registered/{onboardingId}/confirmation")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'TRANSPORTER')")
    BusinessContracts.BusinessProfileResponse confirmRegisteredOnboarding(
            @PathVariable UUID onboardingId, Authentication authentication) {
        UUID userId = authorizationService.authenticatedUserId(authentication);
        return BusinessContracts.BusinessProfileResponse.from(onboardingService.confirm(onboardingId, userId));
    }

    @GetMapping("/{businessId}")
    BusinessContracts.BusinessProfileResponse getBusiness(
            @PathVariable UUID businessId, Authentication authentication) {
        authorizationService.requireBusinessAccess(authentication, businessId);
        return BusinessContracts.BusinessProfileResponse.from(onboardingService.getBusiness(businessId));
    }
}
