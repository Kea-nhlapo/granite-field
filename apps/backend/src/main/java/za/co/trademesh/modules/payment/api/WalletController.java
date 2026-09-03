package za.co.trademesh.modules.payment.api;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.trademesh.modules.payment.application.WalletService;
import za.co.trademesh.shared.security.AuthorizationService;

@RestController
@RequestMapping("/api/businesses/{businessId}/wallet")
public class WalletController {

    private final WalletService wallets;
    private final AuthorizationService authorization;

    public WalletController(WalletService wallets, AuthorizationService authorization) {
        this.wallets = wallets;
        this.authorization = authorization;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_MEMBER', 'ADMINISTRATOR')")
    WalletContracts.WalletResponse get(@PathVariable UUID businessId, Authentication authentication) {
        authorization.requireBusinessAccess(authentication, businessId);
        return WalletContracts.WalletResponse.from(wallets.snapshot(businessId));
    }
}
