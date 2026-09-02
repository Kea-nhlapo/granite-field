package za.co.trademesh.modules.procurement.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderLineItem(
        UUID id,
        UUID sourceRequestItemId,
        String productCode,
        String description,
        BigDecimal quantity,
        UnitOfMeasure unitOfMeasure,
        BigDecimal unitPrice,
        BigDecimal lineTotal) {}
