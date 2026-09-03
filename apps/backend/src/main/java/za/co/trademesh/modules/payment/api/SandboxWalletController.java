package za.co.trademesh.modules.payment.api;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.trademesh.modules.payment.application.SandboxWalletService;
import za.co.trademesh.shared.security.AuthorizationService;

@RestController
@RequestMapping("/api/sandbox")
@ConditionalOnProperty(prefix = "trademesh.sandbox-wallet", name = "enabled", havingValue = "true")
public class SandboxWalletController {

    private final SandboxWalletService wallets;
    private final AuthorizationService authorization;

    public SandboxWalletController(SandboxWalletService wallets, AuthorizationService authorization) {
        this.wallets = wallets;
        this.authorization = authorization;
    }

    @GetMapping("/wallet")
    SandboxWalletContracts.WalletResponse wallet(Authentication authentication) {
        return SandboxWalletContracts.WalletResponse.from(
                wallets.get(authorization.authenticatedUserId(authentication)));
    }

    @GetMapping("/universal-suppliers")
    SandboxWalletContracts.UniversalSuppliersResponse universalSuppliers() {
        List<SandboxWalletContracts.UniversalSupplierResponse> suppliers =
                wallets.universalSupplier().map(SandboxWalletContracts.UniversalSupplierResponse::from).stream()
                        .toList();
        return new SandboxWalletContracts.UniversalSuppliersResponse(suppliers);
    }
}
