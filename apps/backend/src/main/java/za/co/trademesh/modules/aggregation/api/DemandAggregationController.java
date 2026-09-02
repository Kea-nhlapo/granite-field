package za.co.trademesh.modules.aggregation.api;

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
import za.co.trademesh.modules.aggregation.application.DemandAggregationService;
import za.co.trademesh.shared.security.AuthorizationService;

@RestController
@RequestMapping("/api/businesses/{businessId}/aggregation/suggestions")
public class DemandAggregationController {

    private final DemandAggregationService aggregation;
    private final AuthorizationService authorization;

    public DemandAggregationController(DemandAggregationService aggregation, AuthorizationService authorization) {
        this.aggregation = aggregation;
        this.authorization = authorization;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    ResponseEntity<DemandAggregationContracts.SuggestionResponse> suggest(
            @PathVariable UUID businessId,
            @Valid @RequestBody DemandAggregationContracts.SuggestDemandGroupRequest request,
            Authentication authentication) {
        authorization.requireBusinessAccess(authentication, businessId);
        var suggestion =
                aggregation.suggest(businessId, request.toCommand(), authorization.authenticatedUserId(authentication));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DemandAggregationContracts.SuggestionResponse.from(suggestion));
    }

    @GetMapping("/{suggestionId}")
    DemandAggregationContracts.SuggestionResponse get(
            @PathVariable UUID businessId, @PathVariable UUID suggestionId, Authentication authentication) {
        authorization.requireBusinessAccess(authentication, businessId);
        return DemandAggregationContracts.SuggestionResponse.from(aggregation.get(businessId, suggestionId));
    }
}
