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
import za.co.trademesh.modules.document.application.DocumentService;
import za.co.trademesh.shared.security.AuthorizationService;

@RestController
@RequestMapping("/api/businesses/{businessId}/documents")
public class DocumentController {

    private final DocumentService documents;
    private final AuthorizationService authorization;

    public DocumentController(DocumentService documents, AuthorizationService authorization) {
        this.documents = documents;
        this.authorization = authorization;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'SUPPLIER', 'ADMINISTRATOR')")
    ResponseEntity<DocumentContracts.DocumentResponse> register(
            @PathVariable UUID businessId,
            @Valid @RequestBody DocumentContracts.RegisterDocumentRequest request,
            Authentication authentication) {
        authorization.requireBusinessAccess(authentication, businessId);
        UUID actor = authorization.authenticatedUserId(authentication);
        var document =
                documents.register(businessId, request.storedFileId(), request.type(), request.requestId(), actor);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(DocumentContracts.DocumentResponse.from(document));
    }

    @GetMapping("/{documentId}")
    DocumentContracts.DocumentResponse get(
            @PathVariable UUID businessId, @PathVariable UUID documentId, Authentication authentication) {
        authorization.requireBusinessAccess(authentication, businessId);
        return DocumentContracts.DocumentResponse.from(documents.get(businessId, documentId));
    }

    @PostMapping("/{documentId}/confirmations")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'SUPPLIER', 'ADMINISTRATOR')")
    DocumentContracts.DocumentResponse confirm(
            @PathVariable UUID businessId,
            @PathVariable UUID documentId,
            @Valid @RequestBody DocumentContracts.ConfirmDocumentRequest request,
            Authentication authentication) {
        authorization.requireBusinessAccess(authentication, businessId);
        UUID actor = authorization.authenticatedUserId(authentication);
        var fields = request.fields().stream()
                .map(DocumentContracts.ConfirmationFieldRequest::toDomain)
                .toList();
        return DocumentContracts.DocumentResponse.from(
                documents.confirm(businessId, documentId, request.requestId(), fields, actor));
    }
}
