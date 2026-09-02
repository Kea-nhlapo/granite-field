package za.co.trademesh.modules.transport.api;

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
import za.co.trademesh.modules.transport.application.CapacityMatchingService;
import za.co.trademesh.shared.security.AuthorizationService;

@RestController
@RequestMapping("/api/businesses/{businessId}/logistics/capacity-matches")
public class CapacityMatchingController {

    private final CapacityMatchingService matching;
    private final AuthorizationService authorization;

    public CapacityMatchingController(CapacityMatchingService matching, AuthorizationService authorization) {
        this.matching = matching;
        this.authorization = authorization;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    ResponseEntity<CapacityMatchingContracts.SearchResponse> search(
            @PathVariable UUID businessId,
            @Valid @RequestBody CapacityMatchingContracts.SearchRequest request,
            Authentication authentication) {
        authorize(authentication, businessId);
        var created = matching.search(businessId, request.toCommand(), actor(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(CapacityMatchingContracts.SearchResponse.from(created));
    }

    @GetMapping("/{searchId}")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    CapacityMatchingContracts.SearchResponse get(
            @PathVariable UUID businessId, @PathVariable UUID searchId, Authentication authentication) {
        authorize(authentication, businessId);
        return CapacityMatchingContracts.SearchResponse.from(matching.get(businessId, searchId));
    }

    @PostMapping("/{searchId}/reservations")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    ResponseEntity<CapacityMatchingContracts.ReservationResponse> reserve(
            @PathVariable UUID businessId,
            @PathVariable UUID searchId,
            @Valid @RequestBody CapacityMatchingContracts.ReservationRequest request,
            Authentication authentication) {
        authorize(authentication, businessId);
        var reservation = matching.reserve(businessId, searchId, request.toCommand(), actor(authentication));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CapacityMatchingContracts.ReservationResponse.from(reservation));
    }

    @PostMapping("/{searchId}/reservation-release")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    CapacityMatchingContracts.ReservationResponse release(
            @PathVariable UUID businessId, @PathVariable UUID searchId, Authentication authentication) {
        authorize(authentication, businessId);
        return CapacityMatchingContracts.ReservationResponse.from(
                matching.release(businessId, searchId, actor(authentication)));
    }

    private void authorize(Authentication authentication, UUID businessId) {
        authorization.requireBusinessAccess(authentication, businessId);
    }

    private UUID actor(Authentication authentication) {
        return authorization.authenticatedUserId(authentication);
    }
}
