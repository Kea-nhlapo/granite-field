package za.co.trademesh.modules.supplier.api;

import jakarta.servlet.http.HttpServletRequest;
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
import za.co.trademesh.modules.supplier.application.SupplierInvitationService;
import za.co.trademesh.shared.security.AuthorizationService;

@RestController
@RequestMapping("/api")
public class SupplierController {

    private final SupplierInvitationService invitations;
    private final AuthorizationService authorizationService;

    public SupplierController(SupplierInvitationService invitations, AuthorizationService authorizationService) {
        this.invitations = invitations;
        this.authorizationService = authorizationService;
    }

    @PostMapping("/businesses/{buyerBusinessId}/supplier-invitations")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    ResponseEntity<SupplierContracts.CreatedInvitationResponse> invite(
            @PathVariable UUID buyerBusinessId,
            @Valid @RequestBody SupplierContracts.CreateInvitationRequest request,
            Authentication authentication) {
        authorizationService.requireBusinessAccess(authentication, buyerBusinessId);
        UUID userId = authorizationService.authenticatedUserId(authentication);
        var created = invitations.invite(buyerBusinessId, request.requestId(), request.supplierEmail(), userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SupplierContracts.CreatedInvitationResponse.from(created));
    }

    @GetMapping("/supplier-invitations/guest/{token}")
    SupplierContracts.GuestInvitationResponse viewGuest(@PathVariable String token, HttpServletRequest servletRequest) {
        return SupplierContracts.GuestInvitationResponse.from(
                invitations.viewGuestInvitation(token, clientKey(servletRequest)));
    }

    @PostMapping("/supplier-invitations/guest/{token}/responses")
    SupplierContracts.InvitationResponse submitResponse(
            @PathVariable String token,
            @Valid @RequestBody SupplierContracts.SubmitResponseRequest request,
            HttpServletRequest servletRequest) {
        return SupplierContracts.InvitationResponse.from(invitations.submitResponse(
                token, request.requestId(), request.responseReference(), clientKey(servletRequest)));
    }

    @PostMapping("/businesses/{buyerBusinessId}/supplier-invitations/{invitationId}/revocation")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    SupplierContracts.InvitationResponse revoke(
            @PathVariable UUID buyerBusinessId, @PathVariable UUID invitationId, Authentication authentication) {
        authorizationService.requireBusinessAccess(authentication, buyerBusinessId);
        UUID userId = authorizationService.authenticatedUserId(authentication);
        return SupplierContracts.InvitationResponse.from(invitations.revoke(invitationId, buyerBusinessId, userId));
    }

    @PostMapping("/supplier-profiles/{supplierProfileId}/conversion")
    @PreAuthorize("hasAnyRole('SUPPLIER', 'BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    SupplierContracts.SupplierProfileResponse convert(
            @PathVariable UUID supplierProfileId,
            @Valid @RequestBody SupplierContracts.ConvertSupplierRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        UUID userId = authorizationService.authenticatedUserId(authentication);
        if (request.businessId() != null) {
            authorizationService.requireBusinessAccess(authentication, request.businessId());
        }
        return SupplierContracts.SupplierProfileResponse.from(invitations.convert(
                supplierProfileId, userId, request.businessId(), request.invitationToken(), clientKey(servletRequest)));
    }

    private static String clientKey(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
