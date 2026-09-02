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
import za.co.trademesh.modules.routing.application.RoutingService;
import za.co.trademesh.shared.security.AuthorizationService;

@RestController
@RequestMapping("/api/businesses/{businessId}/routing/calculations")
public class RoutingController {

    private final RoutingService routing;
    private final AuthorizationService authorization;

    public RoutingController(RoutingService routing, AuthorizationService authorization) {
        this.routing = routing;
        this.authorization = authorization;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    ResponseEntity<RoutingContracts.RouteCalculationResponse> calculate(
            @PathVariable UUID businessId,
            @Valid @RequestBody RoutingContracts.CalculateRoutesRequest request,
            Authentication authentication) {
        authorize(authentication, businessId);
        var calculation = routing.calculate(businessId, request.toCommand(), actor(authentication));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RoutingContracts.RouteCalculationResponse.from(calculation));
    }

    @GetMapping("/{calculationId}")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    RoutingContracts.RouteCalculationResponse get(
            @PathVariable UUID businessId, @PathVariable UUID calculationId, Authentication authentication) {
        authorize(authentication, businessId);
        return RoutingContracts.RouteCalculationResponse.from(routing.get(businessId, calculationId));
    }

    private void authorize(Authentication authentication, UUID businessId) {
        authorization.requireBusinessAccess(authentication, businessId);
    }

    private UUID actor(Authentication authentication) {
        return authorization.authenticatedUserId(authentication);
    }
}
