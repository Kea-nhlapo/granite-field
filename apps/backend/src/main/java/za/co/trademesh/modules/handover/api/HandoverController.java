package za.co.trademesh.modules.handover.api;

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
import za.co.trademesh.modules.handover.application.HandoverService;
import za.co.trademesh.shared.security.AuthorizationService;

@RestController
@RequestMapping("/api")
public class HandoverController {

    private final HandoverService handovers;
    private final AuthorizationService authorization;

    public HandoverController(HandoverService handovers, AuthorizationService authorization) {
        this.handovers = handovers;
        this.authorization = authorization;
    }

    @PostMapping("/businesses/{businessId}/shipments/{shipmentId}/handovers/challenges")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    ResponseEntity<HandoverContracts.IssuedChallengeResponse> issue(
            @PathVariable UUID businessId,
            @PathVariable UUID shipmentId,
            @Valid @RequestBody HandoverContracts.IssueChallengeRequest request,
            Authentication authentication) {
        authorization.requireBusinessAccess(authentication, businessId);
        var issued = handovers.issue(
                businessId, shipmentId, request.toCommand(), authorization.authenticatedUserId(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(HandoverContracts.IssuedChallengeResponse.from(issued));
    }

    @GetMapping("/businesses/{businessId}/shipments/{shipmentId}/handovers/challenges/{challengeId}")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    HandoverContracts.ChallengeResponse get(
            @PathVariable UUID businessId,
            @PathVariable UUID shipmentId,
            @PathVariable UUID challengeId,
            Authentication authentication) {
        authorization.requireBusinessAccess(authentication, businessId);
        return HandoverContracts.ChallengeResponse.from(handovers.get(businessId, shipmentId, challengeId));
    }

    @PostMapping("/handovers/confirmations")
    @PreAuthorize("isAuthenticated()")
    HandoverContracts.ChallengeResponse confirm(
            @Valid @RequestBody HandoverContracts.ConfirmHandoverRequest request, Authentication authentication) {
        return HandoverContracts.ChallengeResponse.from(
                handovers.confirm(request.toCommand(), authorization.authenticatedUserId(authentication)));
    }
}
