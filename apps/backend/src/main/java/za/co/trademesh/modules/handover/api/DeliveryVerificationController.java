package za.co.trademesh.modules.handover.api;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import za.co.trademesh.modules.handover.application.HandoverService;
import za.co.trademesh.modules.handover.domain.HandoverType;
import za.co.trademesh.shared.security.AuthorizationService;

@RestController
@RequestMapping("/api/delivery/{shipmentId}")
public class DeliveryVerificationController {

    private final HandoverService handovers;
    private final DeliveryVerificationSseBroker stream;
    private final AuthorizationService authorization;

    public DeliveryVerificationController(
            HandoverService handovers, DeliveryVerificationSseBroker stream, AuthorizationService authorization) {
        this.handovers = handovers;
        this.stream = stream;
        this.authorization = authorization;
    }

    @PostMapping("/qr")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    ResponseEntity<HandoverContracts.IssuedChallengeResponse> issue(
            @PathVariable UUID shipmentId,
            @Valid @RequestBody DeliveryVerificationContracts.IssueQrRequest request,
            Authentication authentication) {
        authorization.requireBusinessAccess(authentication, request.businessId());
        var issued = handovers.issue(
                request.businessId(),
                shipmentId,
                new HandoverService.IssueChallenge(
                        HandoverType.DELIVERY, request.deliveryOrderId(), request.counterpartyUserId()),
                authorization.authenticatedUserId(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(HandoverContracts.IssuedChallengeResponse.from(issued));
    }

    @PostMapping("/scan")
    @PreAuthorize("isAuthenticated()")
    HandoverContracts.ChallengeResponse scan(
            @PathVariable UUID shipmentId,
            @Valid @RequestBody DeliveryVerificationContracts.ScanRequest request,
            Authentication authentication) {
        return HandoverContracts.ChallengeResponse.from(handovers.scanDelivery(
                shipmentId, request.toCommand(), authorization.authenticatedUserId(authentication)));
    }

    @GetMapping("/verification")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    DeliveryVerificationContracts.StatusResponse get(
            @PathVariable UUID shipmentId, @RequestParam UUID businessId, Authentication authentication) {
        authorization.requireBusinessAccess(authentication, businessId);
        return DeliveryVerificationContracts.StatusResponse.from(handovers.deliveryStatus(businessId, shipmentId));
    }

    @GetMapping(value = "/verification/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiResponse(
            responseCode = "200",
            description = "Current and subsequent delivery verification status events",
            content =
                    @Content(
                            mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                            schema = @Schema(implementation = DeliveryVerificationContracts.StatusResponse.class)))
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    SseEmitter events(@PathVariable UUID shipmentId, @RequestParam UUID businessId, Authentication authentication) {
        authorization.requireBusinessAccess(authentication, businessId);
        return stream.subscribe(handovers.deliveryStatus(businessId, shipmentId));
    }
}
