package za.co.trademesh.modules.trust.api;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.trademesh.modules.trust.application.PremiumEstimateService;
import za.co.trademesh.modules.trust.application.TrustScoreService;
import za.co.trademesh.modules.trust.application.TrustService;
import za.co.trademesh.shared.security.AuthorizationService;

@RestController
public class TrustController {

    private final TrustService trust;
    private final TrustScoreService scores;
    private final PremiumEstimateService premiums;
    private final AuthorizationService authorization;

    public TrustController(
            TrustService trust,
            TrustScoreService scores,
            PremiumEstimateService premiums,
            AuthorizationService authorization) {
        this.trust = trust;
        this.scores = scores;
        this.premiums = premiums;
        this.authorization = authorization;
    }

    @GetMapping("/api/public/businesses/{businessId}/trust")
    TrustContracts.PublicSummaryResponse publicSummary(@PathVariable UUID businessId) {
        return TrustContracts.PublicSummaryResponse.from(trust.getPublicSummary(businessId));
    }

    @PostMapping("/api/internal/trust/businesses/{businessId}/recalculation")
    @PreAuthorize("hasAnyRole('INTERNAL_RISK_ANALYST', 'ADMINISTRATOR')")
    TrustContracts.InternalCalculationResponse recalculate(
            @PathVariable UUID businessId, Authentication authentication) {
        authorization.requireInternalRiskAccess(authentication);
        return TrustContracts.InternalCalculationResponse.from(trust.recalculate(businessId));
    }

    @GetMapping("/api/users/{userId}/trust")
    @PreAuthorize("isAuthenticated()")
    TrustContracts.ScoreResponse score(@PathVariable UUID userId, Authentication authentication) {
        authorization.requireSelfOrAdministrator(authentication, userId);
        return TrustContracts.ScoreResponse.from(userId, scores.getForUser(userId));
    }

    @GetMapping("/api/delivery/{shipmentId}/premium-estimate")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    TrustContracts.PremiumEstimateResponse premiumEstimate(
            @PathVariable UUID shipmentId, Authentication authentication) {
        UUID businessId = premiums.requireBusinessId(shipmentId);
        authorization.requireBusinessAccess(authentication, businessId);
        return TrustContracts.PremiumEstimateResponse.from(premiums.estimate(shipmentId));
    }
}
