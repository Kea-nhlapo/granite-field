package za.co.trademesh.modules.payment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.trademesh.modules.payment.application.SandboxWalletService;
import za.co.trademesh.modules.payment.application.SandboxWalletSnapshot;
import za.co.trademesh.modules.payment.domain.SandboxWalletEntry;

final class SandboxWalletContracts {

    private SandboxWalletContracts() {}

    record WalletResponse(
            UUID userId,
            String displayName,
            String currency,
            BigDecimal availableBalance,
            BigDecimal heldBalance,
            Instant updatedAt,
            List<EntryResponse> entries) {
        static WalletResponse from(SandboxWalletSnapshot wallet) {
            return new WalletResponse(
                    wallet.userId(),
                    wallet.displayName(),
                    wallet.currency(),
                    wallet.availableBalance(),
                    wallet.heldBalance(),
                    wallet.updatedAt(),
                    wallet.entries().stream().map(EntryResponse::from).toList());
        }
    }

    record EntryResponse(
            UUID entryId,
            SandboxWalletEntry.EntryType type,
            BigDecimal availableDelta,
            BigDecimal heldDelta,
            BigDecimal availableBalanceAfter,
            BigDecimal heldBalanceAfter,
            String description,
            Instant createdAt) {
        static EntryResponse from(SandboxWalletEntry entry) {
            return new EntryResponse(
                    entry.id(),
                    entry.type(),
                    entry.availableDelta(),
                    entry.heldDelta(),
                    entry.availableBalanceAfter(),
                    entry.heldBalanceAfter(),
                    entry.description(),
                    entry.createdAt());
        }
    }

    record UniversalSupplierResponse(UUID userId, UUID supplierProfileId, String displayName, String loginEmail) {
        static UniversalSupplierResponse from(SandboxWalletService.UniversalSupplier supplier) {
            return new UniversalSupplierResponse(
                    supplier.userId(), supplier.supplierProfileId(), supplier.displayName(), supplier.loginEmail());
        }
    }

    record UniversalSuppliersResponse(List<UniversalSupplierResponse> suppliers) {}
}
