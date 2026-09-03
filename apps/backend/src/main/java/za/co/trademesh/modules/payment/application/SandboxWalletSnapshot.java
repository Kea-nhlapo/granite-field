package za.co.trademesh.modules.payment.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.trademesh.modules.payment.domain.SandboxWallet;
import za.co.trademesh.modules.payment.domain.SandboxWalletEntry;

public record SandboxWalletSnapshot(
        UUID userId,
        String displayName,
        String currency,
        BigDecimal availableBalance,
        BigDecimal heldBalance,
        Instant updatedAt,
        List<SandboxWalletEntry> entries) {

    static SandboxWalletSnapshot from(SandboxWallet wallet, List<SandboxWalletEntry> entries) {
        return new SandboxWalletSnapshot(
                wallet.userId(),
                wallet.displayName(),
                wallet.currency(),
                wallet.availableBalance(),
                wallet.heldBalance(),
                wallet.updatedAt(),
                List.copyOf(entries));
    }
}
