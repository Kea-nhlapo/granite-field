package za.co.trademesh.modules.payment.api;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
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
import za.co.trademesh.modules.payment.application.EscrowService;
import za.co.trademesh.shared.security.AuthorizationService;

@RestController
@RequestMapping("/api/delivery/{shipmentId}")
public class EscrowController {

    private final EscrowService escrows;
    private final EscrowSseBroker stream;
    private final AuthorizationService authorization;

    public EscrowController(EscrowService escrows, EscrowSseBroker stream, AuthorizationService authorization) {
        this.escrows = escrows;
        this.stream = stream;
        this.authorization = authorization;
    }

    @GetMapping("/escrow")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    EscrowContracts.EscrowResponse get(
            @PathVariable UUID shipmentId, @RequestParam UUID businessId, Authentication authentication) {
        authorization.requireBusinessAccess(authentication, businessId);
        return EscrowContracts.EscrowResponse.from(escrows.get(businessId, shipmentId));
    }

    @GetMapping(value = "/escrow/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiResponse(
            responseCode = "200",
            description = "Current and subsequent escrow status events",
            content =
                    @Content(
                            mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                            schema = @Schema(implementation = EscrowContracts.EscrowResponse.class)))
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    SseEmitter events(@PathVariable UUID shipmentId, @RequestParam UUID businessId, Authentication authentication) {
        authorization.requireBusinessAccess(authentication, businessId);
        return stream.subscribe(escrows.get(businessId, shipmentId));
    }

    @PostMapping("/escrow/retry")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'ADMINISTRATOR')")
    EscrowContracts.EscrowResponse retry(
            @PathVariable UUID shipmentId,
            @Valid @RequestBody EscrowContracts.RetryEscrowRequest request,
            Authentication authentication) {
        authorization.requireBusinessAccess(authentication, request.businessId());
        return EscrowContracts.EscrowResponse.from(
                escrows.retryLock(request.businessId(), shipmentId, request.requestId()));
    }

    @PostMapping("/release")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'ADMINISTRATOR')")
    EscrowContracts.EscrowResponse release(
            @PathVariable UUID shipmentId,
            @Valid @RequestBody EscrowContracts.ReleaseEscrowRequest request,
            Authentication authentication) {
        authorization.requireBusinessAccess(authentication, request.businessId());
        return EscrowContracts.EscrowResponse.from(
                escrows.release(request.businessId(), shipmentId, request.requestId(), request.resolvedAmount()));
    }

    @PostMapping("/resolve")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'ADMINISTRATOR')")
    EscrowContracts.EscrowResponse resolve(
            @PathVariable UUID shipmentId,
            @Valid @RequestBody EscrowContracts.ResolveEscrowRequest request,
            Authentication authentication) {
        authorization.requireBusinessAccess(authentication, request.businessId());
        return EscrowContracts.EscrowResponse.from(escrows.resolveAndRelease(
                request.businessId(),
                shipmentId,
                request.requestId(),
                request.resolvedAmount(),
                authorization.authenticatedUserId(authentication)));
    }
}
