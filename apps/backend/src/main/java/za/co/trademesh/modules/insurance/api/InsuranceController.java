package za.co.trademesh.modules.insurance.api;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import za.co.trademesh.modules.insurance.application.InsuranceService;
import za.co.trademesh.shared.security.AuthorizationService;

@RestController
public class InsuranceController {

    private final InsuranceService insurance;
    private final AuthorizationService authorization;

    public InsuranceController(InsuranceService insurance, AuthorizationService authorization) {
        this.insurance = insurance;
        this.authorization = authorization;
    }

    @PostMapping("/api/internal/insurance/cases")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('INTERNAL_RISK_ANALYST', 'ADMINISTRATOR')")
    InsuranceContracts.CaseResponse createCase(
            @Valid @RequestBody InsuranceContracts.CreateCaseRequest request, Authentication authentication) {
        authorization.requireInternalRiskAccess(authentication);
        return InsuranceContracts.CaseResponse.from(
                insurance.createCase(request.toCommand(), authorization.authenticatedUserId(authentication)));
    }

    @GetMapping("/api/insurance/cases/{caseId}/evidence")
    @PreAuthorize("isAuthenticated()")
    InsuranceContracts.EvidencePackageResponse evidence(@PathVariable UUID caseId, Authentication authentication) {
        return InsuranceContracts.EvidencePackageResponse.from(
                insurance.viewEvidence(caseId, authorization.authenticatedUserId(authentication)));
    }

    @PostMapping("/api/insurance/cases/{caseId}/decisions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    InsuranceContracts.DecisionResponse recordDecision(
            @PathVariable UUID caseId,
            @Valid @RequestBody InsuranceContracts.RecordDecisionRequest request,
            Authentication authentication) {
        return InsuranceContracts.DecisionResponse.from(insurance.recordDecision(
                caseId, request.toCommand(), authorization.authenticatedUserId(authentication)));
    }
}
