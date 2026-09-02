package za.co.trademesh.modules.document.api;

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
import za.co.trademesh.modules.document.application.DocumentComparisonService;
import za.co.trademesh.shared.security.AuthorizationService;

@RestController
@RequestMapping("/api/businesses/{businessId}/documents/comparisons")
public class DocumentComparisonController {

    private final DocumentComparisonService comparisons;
    private final AuthorizationService authorization;

    public DocumentComparisonController(DocumentComparisonService comparisons, AuthorizationService authorization) {
        this.comparisons = comparisons;
        this.authorization = authorization;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'SUPPLIER', 'ADMINISTRATOR')")
    ResponseEntity<DocumentComparisonContracts.ComparisonResponse> compare(
            @PathVariable UUID businessId,
            @Valid @RequestBody DocumentComparisonContracts.CompareDocumentsRequest request,
            Authentication authentication) {
        authorization.requireBusinessAccess(authentication, businessId);
        UUID actorUserId = authorization.authenticatedUserId(authentication);
        var comparison = comparisons.compare(businessId, request.toCommand(), actorUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DocumentComparisonContracts.ComparisonResponse.from(comparison));
    }

    @GetMapping("/{comparisonId}")
    DocumentComparisonContracts.ComparisonResponse get(
            @PathVariable UUID businessId, @PathVariable UUID comparisonId, Authentication authentication) {
        authorization.requireBusinessAccess(authentication, businessId);
        return DocumentComparisonContracts.ComparisonResponse.from(comparisons.get(businessId, comparisonId));
    }
}
