package za.co.trademesh.modules.risk.api;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.trademesh.modules.risk.application.RiskService;
import za.co.trademesh.shared.security.AuthorizationService;

@RestController
@RequestMapping("/api/internal/risk")
public class RiskController {

    private final RiskService risk;
    private final AuthorizationService authorization;

    public RiskController(RiskService risk, AuthorizationService authorization) {
        this.risk = risk;
        this.authorization = authorization;
    }

    @GetMapping("/shipments/{shipmentId}/indicators")
    @PreAuthorize("hasAnyRole('INTERNAL_RISK_ANALYST', 'ADMINISTRATOR')")
    RiskContracts.IndicatorListResponse list(@PathVariable UUID shipmentId, Authentication authentication) {
        authorization.requireInternalRiskAccess(authentication);
        return RiskContracts.IndicatorListResponse.from(risk.listForShipment(shipmentId));
    }

    @PostMapping("/indicators/{indicatorId}/transitions")
    @PreAuthorize("hasAnyRole('INTERNAL_RISK_ANALYST', 'ADMINISTRATOR')")
    RiskContracts.IndicatorResponse transition(
            @PathVariable UUID indicatorId,
            @Valid @RequestBody RiskContracts.TransitionRequest request,
            Authentication authentication) {
        authorization.requireInternalRiskAccess(authentication);
        return RiskContracts.IndicatorResponse.from(
                risk.transition(indicatorId, request.toCommand(), authorization.authenticatedUserId(authentication)));
    }
}
