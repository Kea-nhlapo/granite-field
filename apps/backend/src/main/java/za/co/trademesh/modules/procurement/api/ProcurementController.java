package za.co.trademesh.modules.procurement.api;

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
import za.co.trademesh.modules.procurement.application.ProcurementService;
import za.co.trademesh.shared.security.AuthorizationService;

@RestController
@RequestMapping("/api/businesses/{businessId}/procurement")
public class ProcurementController {

    private final ProcurementService procurement;
    private final AuthorizationService authorization;

    public ProcurementController(ProcurementService procurement, AuthorizationService authorization) {
        this.procurement = procurement;
        this.authorization = authorization;
    }

    @PostMapping("/requests")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    ResponseEntity<ProcurementContracts.ProductRequestResponse> createRequest(
            @PathVariable UUID businessId,
            @Valid @RequestBody ProcurementContracts.CreateProductRequest request,
            Authentication authentication) {
        authorize(authentication, businessId);
        var created = procurement.createRequest(businessId, request.toCommand(), actor(authentication));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProcurementContracts.ProductRequestResponse.from(created));
    }

    @GetMapping("/requests/{requestId}")
    ProcurementContracts.ProductRequestResponse getRequest(
            @PathVariable UUID businessId, @PathVariable UUID requestId, Authentication authentication) {
        authorize(authentication, businessId);
        return ProcurementContracts.ProductRequestResponse.from(procurement.getRequest(businessId, requestId));
    }

    @PostMapping("/requests/{requestId}/cancellation")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    ProcurementContracts.ProductRequestResponse cancelRequest(
            @PathVariable UUID businessId, @PathVariable UUID requestId, Authentication authentication) {
        authorize(authentication, businessId);
        return ProcurementContracts.ProductRequestResponse.from(procurement.cancelRequest(businessId, requestId));
    }

    @PostMapping("/requests/{requestId}/quotes")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'SUPPLIER', 'ADMINISTRATOR')")
    ResponseEntity<ProcurementContracts.QuoteResponse> createQuote(
            @PathVariable UUID businessId,
            @PathVariable UUID requestId,
            @Valid @RequestBody ProcurementContracts.CreateQuoteRequest request,
            Authentication authentication) {
        authorize(authentication, businessId);
        var created = procurement.createQuote(businessId, requestId, request.toCommand(), actor(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(ProcurementContracts.QuoteResponse.from(created));
    }

    @GetMapping("/quotes/{quoteId}")
    ProcurementContracts.QuoteResponse getQuote(
            @PathVariable UUID businessId, @PathVariable UUID quoteId, Authentication authentication) {
        authorize(authentication, businessId);
        return ProcurementContracts.QuoteResponse.from(procurement.getQuote(businessId, quoteId));
    }

    @PostMapping("/quotes/{quoteId}/confirmations")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    ResponseEntity<ProcurementContracts.OrderResponse> confirmQuote(
            @PathVariable UUID businessId,
            @PathVariable UUID quoteId,
            @Valid @RequestBody ProcurementContracts.ConfirmQuoteRequest request,
            Authentication authentication) {
        authorize(authentication, businessId);
        var order = procurement.confirmQuote(businessId, quoteId, request.requestId(), actor(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(ProcurementContracts.OrderResponse.from(order));
    }

    @GetMapping("/orders/{orderId}")
    ProcurementContracts.OrderResponse getOrder(
            @PathVariable UUID businessId, @PathVariable UUID orderId, Authentication authentication) {
        authorize(authentication, businessId);
        return ProcurementContracts.OrderResponse.from(procurement.getOrder(businessId, orderId));
    }

    private void authorize(Authentication authentication, UUID businessId) {
        authorization.requireBusinessAccess(authentication, businessId);
    }

    private UUID actor(Authentication authentication) {
        return authorization.authenticatedUserId(authentication);
    }
}
