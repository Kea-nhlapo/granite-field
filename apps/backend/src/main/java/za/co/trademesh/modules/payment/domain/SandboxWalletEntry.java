package za.co.trademesh.modules.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SandboxWalletEntry(
        UUID id,
        UUID userId,
        String referenceKey,
        EntryType type,
        BigDecimal availableDelta,
        BigDecimal heldDelta,
        BigDecimal availableBalanceAfter,
        BigDecimal heldBalanceAfter,
        String description,
        Instant createdAt) {

    public enum EntryType {
        OPENING_CREDIT,
        ESCROW_HELD,
        ESCROW_SETTLED,
        PAYMENT_RECEIVED
    }
}
