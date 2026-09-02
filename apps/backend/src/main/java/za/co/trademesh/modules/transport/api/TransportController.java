package za.co.trademesh.modules.transport.api;

import jakarta.validation.Valid;
import java.util.List;
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
import za.co.trademesh.modules.transport.application.TransportService;
import za.co.trademesh.shared.security.AuthorizationService;

@RestController
@RequestMapping("/api/businesses/{businessId}/transport")
public class TransportController {

    private final TransportService transport;
    private final AuthorizationService authorization;

    public TransportController(TransportService transport, AuthorizationService authorization) {
        this.transport = transport;
        this.authorization = authorization;
    }

    @PostMapping("/profile")
    @PreAuthorize("hasAnyRole('TRANSPORTER', 'ADMINISTRATOR')")
    ResponseEntity<TransportContracts.TransporterResponse> register(
            @PathVariable UUID businessId,
            @Valid @RequestBody TransportContracts.RegisterTransporterRequest request,
            Authentication authentication) {
        authorize(authentication, businessId);
        var created = transport.registerTransporter(businessId, request.displayName(), actor(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(TransportContracts.TransporterResponse.from(created));
    }

    @GetMapping("/profile")
    @PreAuthorize("hasAnyRole('TRANSPORTER', 'ADMINISTRATOR')")
    TransportContracts.TransporterResponse getProfile(@PathVariable UUID businessId, Authentication authentication) {
        authorize(authentication, businessId);
        return TransportContracts.TransporterResponse.from(transport.getTransporter(businessId));
    }

    @PostMapping("/vehicles")
    @PreAuthorize("hasAnyRole('TRANSPORTER', 'ADMINISTRATOR')")
    ResponseEntity<TransportContracts.VehicleResponse> createVehicle(
            @PathVariable UUID businessId,
            @Valid @RequestBody TransportContracts.CreateVehicleRequest request,
            Authentication authentication) {
        authorize(authentication, businessId);
        var created = transport.createVehicle(businessId, request.toCommand(), actor(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(TransportContracts.VehicleResponse.from(created));
    }

    @GetMapping("/vehicles/{vehicleId}")
    @PreAuthorize("hasAnyRole('TRANSPORTER', 'ADMINISTRATOR')")
    TransportContracts.VehicleResponse getVehicle(
            @PathVariable UUID businessId, @PathVariable UUID vehicleId, Authentication authentication) {
        authorize(authentication, businessId);
        return TransportContracts.VehicleResponse.from(transport.getVehicle(businessId, vehicleId));
    }

    @PostMapping("/drivers")
    @PreAuthorize("hasAnyRole('TRANSPORTER', 'ADMINISTRATOR')")
    ResponseEntity<TransportContracts.DriverResponse> createDriver(
            @PathVariable UUID businessId,
            @Valid @RequestBody TransportContracts.CreateDriverRequest request,
            Authentication authentication) {
        authorize(authentication, businessId);
        var created = transport.createDriver(businessId, request.toCommand(), actor(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(TransportContracts.DriverResponse.from(created));
    }

    @GetMapping("/drivers/{driverId}")
    @PreAuthorize("hasAnyRole('TRANSPORTER', 'ADMINISTRATOR')")
    TransportContracts.DriverResponse getDriver(
            @PathVariable UUID businessId, @PathVariable UUID driverId, Authentication authentication) {
        authorize(authentication, businessId);
        return TransportContracts.DriverResponse.from(transport.getDriver(businessId, driverId));
    }

    @PostMapping("/assignments")
    @PreAuthorize("hasAnyRole('TRANSPORTER', 'ADMINISTRATOR')")
    ResponseEntity<TransportContracts.AssignmentResponse> assignDriver(
            @PathVariable UUID businessId,
            @Valid @RequestBody TransportContracts.AssignDriverRequest request,
            Authentication authentication) {
        authorize(authentication, businessId);
        var created = transport.assignDriver(businessId, request.toCommand(), actor(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(TransportContracts.AssignmentResponse.from(created));
    }

    @PostMapping("/assignments/{assignmentId}/end")
    @PreAuthorize("hasAnyRole('TRANSPORTER', 'ADMINISTRATOR')")
    TransportContracts.AssignmentResponse endAssignment(
            @PathVariable UUID businessId, @PathVariable UUID assignmentId, Authentication authentication) {
        authorize(authentication, businessId);
        return TransportContracts.AssignmentResponse.from(
                transport.endAssignment(businessId, assignmentId, actor(authentication)));
    }

    @GetMapping("/vehicles/{vehicleId}/assignments")
    @PreAuthorize("hasAnyRole('TRANSPORTER', 'ADMINISTRATOR')")
    List<TransportContracts.AssignmentResponse> assignmentHistory(
            @PathVariable UUID businessId, @PathVariable UUID vehicleId, Authentication authentication) {
        authorize(authentication, businessId);
        return transport.vehicleAssignmentHistory(businessId, vehicleId).stream()
                .map(TransportContracts.AssignmentResponse::from)
                .toList();
    }

    @PostMapping("/capacity-offers")
    @PreAuthorize("hasAnyRole('TRANSPORTER', 'ADMINISTRATOR')")
    ResponseEntity<TransportContracts.CapacityOfferResponse> publishOffer(
            @PathVariable UUID businessId,
            @Valid @RequestBody TransportContracts.PublishCapacityOfferRequest request,
            Authentication authentication) {
        authorize(authentication, businessId);
        var created = transport.publishOffer(businessId, request.toCommand(), actor(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(TransportContracts.CapacityOfferResponse.from(created));
    }

    @GetMapping("/capacity-offers/{offerId}")
    @PreAuthorize("hasAnyRole('TRANSPORTER', 'ADMINISTRATOR')")
    TransportContracts.CapacityOfferResponse getOffer(
            @PathVariable UUID businessId, @PathVariable UUID offerId, Authentication authentication) {
        authorize(authentication, businessId);
        return TransportContracts.CapacityOfferResponse.from(transport.getOffer(businessId, offerId));
    }

    @PostMapping("/capacity-offers/{offerId}/cancellation")
    @PreAuthorize("hasAnyRole('TRANSPORTER', 'ADMINISTRATOR')")
    TransportContracts.CapacityOfferResponse cancelOffer(
            @PathVariable UUID businessId, @PathVariable UUID offerId, Authentication authentication) {
        authorize(authentication, businessId);
        return TransportContracts.CapacityOfferResponse.from(
                transport.cancelOffer(businessId, offerId, actor(authentication)));
    }

    private void authorize(Authentication authentication, UUID businessId) {
        authorization.requireBusinessAccess(authentication, businessId);
    }

    private UUID actor(Authentication authentication) {
        return authorization.authenticatedUserId(authentication);
    }
}
