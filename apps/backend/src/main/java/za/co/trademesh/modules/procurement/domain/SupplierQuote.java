package za.co.trademesh.modules.procurement.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SupplierQuote(
        UUID id,
        UUID requestId,
        UUID buyerBusinessId,
        UUID supplierProfileId,
        UUID sourceDocumentId,
        QuoteStatus status,
        String currency,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal total,
        Instant validUntil,
        List<QuoteLineItem> items,
        UUID createdByUserId,
        Instant createdAt) {

    public SupplierQuote {
        items = List.copyOf(items);
    }
}
