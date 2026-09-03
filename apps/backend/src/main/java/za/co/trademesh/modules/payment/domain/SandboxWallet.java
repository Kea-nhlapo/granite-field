package za.co.trademesh.modules.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SandboxWallet(
        UUID userId,
        String displayName,
        String currency,
        BigDecimal availableBalance,
        BigDecimal heldBalance,
        Instant updatedAt) {}
