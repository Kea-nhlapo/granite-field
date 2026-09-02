package za.co.trademesh.modules.routing.api;

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
import za.co.trademesh.modules.routing.application.RouteScoringService;
import za.co.trademesh.shared.security.AuthorizationService;

@RestController
@RequestMapping("/api/businesses/{businessId}/routing")
public class RouteScoringController {

    private final RouteScoringService scoring;
    private final AuthorizationService authorization;

    public RouteScoringController(RouteScoringService scoring, AuthorizationService authorization) {
        this.scoring = scoring;
        this.authorization = authorization;
    }

    @PostMapping("/calculations/{calculationId}/assessments")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    ResponseEntity<RouteScoringContracts.RouteAssessmentResponse> score(
            @PathVariable UUID businessId,
            @PathVariable UUID calculationId,
            @Valid @RequestBody RouteScoringContracts.ScoreRoutesRequest request,
            Authentication authentication) {
        authorize(authentication, businessId);
        var assessment = scoring.score(businessId, calculationId, request.toCommand(), actor(authentication));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RouteScoringContracts.RouteAssessmentResponse.from(assessment));
    }

    @GetMapping("/assessments/{assessmentId}")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    RouteScoringContracts.RouteAssessmentResponse get(
            @PathVariable UUID businessId, @PathVariable UUID assessmentId, Authentication authentication) {
        authorize(authentication, businessId);
        return RouteScoringContracts.RouteAssessmentResponse.from(scoring.get(businessId, assessmentId));
    }

    private void authorize(Authentication authentication, UUID businessId) {
        authorization.requireBusinessAccess(authentication, businessId);
    }

    private UUID actor(Authentication authentication) {
        return authorization.authenticatedUserId(authentication);
    }
}
