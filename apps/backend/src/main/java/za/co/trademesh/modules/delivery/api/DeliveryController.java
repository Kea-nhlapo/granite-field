package za.co.trademesh.modules.delivery.api;

import jakarta.validation.Valid;
import java.io.IOException;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import za.co.trademesh.modules.delivery.application.DeliveryException;
import za.co.trademesh.modules.delivery.application.DeliveryProposalService;
import za.co.trademesh.modules.delivery.application.VoiceSupplierSearchService;
import za.co.trademesh.shared.security.AuthorizationService;

@RestController
@RequestMapping("/api/delivery")
public class DeliveryController {

    private final VoiceSupplierSearchService voiceSearch;
    private final DeliveryProposalService proposals;
    private final AuthorizationService authorization;

    public DeliveryController(
            VoiceSupplierSearchService voiceSearch,
            DeliveryProposalService proposals,
            AuthorizationService authorization) {
        this.voiceSearch = voiceSearch;
        this.proposals = proposals;
        this.authorization = authorization;
    }

    @PostMapping(value = "/voice-search", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    DeliveryContracts.VoiceSearchResponse voiceSearch(
            @RequestPart("audio") MultipartFile audio, @RequestParam double latitude, @RequestParam double longitude) {
        try {
            return DeliveryContracts.VoiceSearchResponse.from(
                    voiceSearch.search(audio.getBytes(), audio.getContentType(), latitude, longitude));
        } catch (IOException readFailure) {
            throw DeliveryException.invalidVoiceRequest();
        }
    }

    @PostMapping("/{shipmentId}/propose")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    ResponseEntity<DeliveryContracts.DeliveryProposalResponse> propose(
            @PathVariable UUID shipmentId,
            @Valid @RequestBody DeliveryContracts.ProposeDeliveryRequest request,
            Authentication authentication) {
        authorization.requireBusinessAccess(authentication, request.businessId());
        UUID actor = authorization.authenticatedUserId(authentication);
        var created = proposals.propose(request.businessId(), shipmentId, request.toCommand(), actor);
        HttpStatus status = created.newlyCreated() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(DeliveryContracts.DeliveryProposalResponse.from(created));
    }

    /**
     * Safe preview for an email or mobile link. A GET never accepts a delivery,
     * because email security scanners routinely open links automatically.
     */
    @GetMapping("/confirm/{token}")
    DeliveryContracts.DeliveryProposalResponse preview(@PathVariable String token) {
        return DeliveryContracts.DeliveryProposalResponse.from(proposals.preview(token));
    }

    @PostMapping("/confirm/{token}")
    DeliveryContracts.DeliveryProposalResponse confirm(@PathVariable String token) {
        return DeliveryContracts.DeliveryProposalResponse.from(proposals.confirm(token));
    }
}
