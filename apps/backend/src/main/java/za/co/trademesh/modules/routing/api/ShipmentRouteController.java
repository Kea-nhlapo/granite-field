package za.co.trademesh.modules.routing.api;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.trademesh.modules.routing.application.ShipmentRouteLookupService;
import za.co.trademesh.shared.security.AuthorizationService;

@RestController
@RequestMapping("/api/delivery")
public class ShipmentRouteController {

    private final ShipmentRouteLookupService routes;
    private final AuthorizationService authorization;

    public ShipmentRouteController(ShipmentRouteLookupService routes, AuthorizationService authorization) {
        this.routes = routes;
        this.authorization = authorization;
    }

    @GetMapping("/{shipmentId}/route")
    @PreAuthorize(
            "hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'SUPPLIER', 'TRANSPORTER', 'DRIVER', 'ADMINISTRATOR')")
    ShipmentRouteContracts.RouteResponse route(
            @PathVariable UUID shipmentId, @RequestParam UUID businessId, Authentication authentication) {
        authorization.requireBusinessAccess(authentication, businessId);
        return ShipmentRouteContracts.RouteResponse.from(routes.route(businessId, shipmentId));
    }
}
