package za.co.trademesh.modules.procurement.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record QuoteLineItem(
        UUID id,
        UUID requestItemId,
        String description,
        BigDecimal quantity,
        UnitOfMeasure unitOfMeasure,
        BigDecimal unitPrice,
        BigDecimal lineTotal) {}
