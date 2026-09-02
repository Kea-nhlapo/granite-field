package za.co.trademesh.modules.shipment.api;

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
import za.co.trademesh.modules.shipment.application.ShipmentService;
import za.co.trademesh.modules.shipment.domain.ShipmentActionSource;
import za.co.trademesh.shared.security.AuthorizationService;

@RestController
@RequestMapping("/api/businesses/{businessId}/shipments")
public class ShipmentController {

    private final ShipmentService shipments;
    private final AuthorizationService authorization;

    public ShipmentController(ShipmentService shipments, AuthorizationService authorization) {
        this.shipments = shipments;
        this.authorization = authorization;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    ResponseEntity<ShipmentContracts.ShipmentResponse> create(
            @PathVariable UUID businessId,
            @Valid @RequestBody ShipmentContracts.CreateShipmentRequest request,
            Authentication authentication) {
        authorize(authentication, businessId);
        var shipment =
                shipments.create(businessId, request.toCommand(), actor(authentication), ShipmentActionSource.API);
        return ResponseEntity.status(HttpStatus.CREATED).body(ShipmentContracts.ShipmentResponse.from(shipment));
    }

    @GetMapping("/{shipmentId}")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    ShipmentContracts.ShipmentResponse get(
            @PathVariable UUID businessId, @PathVariable UUID shipmentId, Authentication authentication) {
        authorize(authentication, businessId);
        return ShipmentContracts.ShipmentResponse.from(shipments.get(businessId, shipmentId));
    }

    @PostMapping("/{shipmentId}/transitions")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    ShipmentContracts.ShipmentResponse transition(
            @PathVariable UUID businessId,
            @PathVariable UUID shipmentId,
            @Valid @RequestBody ShipmentContracts.TransitionShipmentRequest request,
            Authentication authentication) {
        authorize(authentication, businessId);
        return ShipmentContracts.ShipmentResponse.from(shipments.transition(
                businessId, shipmentId, request.toCommand(), actor(authentication), ShipmentActionSource.API));
    }

    @PostMapping("/{shipmentId}/assignments")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    ShipmentContracts.ShipmentResponse changeAssignment(
            @PathVariable UUID businessId,
            @PathVariable UUID shipmentId,
            @Valid @RequestBody ShipmentContracts.ChangeAssignmentRequest request,
            Authentication authentication) {
        authorize(authentication, businessId);
        return ShipmentContracts.ShipmentResponse.from(shipments.changeAssignment(
                businessId, shipmentId, request.toCommand(), actor(authentication), ShipmentActionSource.API));
    }

    private void authorize(Authentication authentication, UUID businessId) {
        authorization.requireBusinessAccess(authentication, businessId);
    }

    private UUID actor(Authentication authentication) {
        return authorization.authenticatedUserId(authentication);
    }
}
