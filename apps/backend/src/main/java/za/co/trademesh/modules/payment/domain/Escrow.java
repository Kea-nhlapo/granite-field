package za.co.trademesh.modules.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Escrow(
        UUID id,
        UUID shipmentId,
        UUID businessId,
        UUID supplierProfileId,
        String protectedSupplierPhone,
        String currency,
        BigDecimal agreedAmount,
        EscrowStatus status,
        Instant createdAt,
        Instant updatedAt) {}
