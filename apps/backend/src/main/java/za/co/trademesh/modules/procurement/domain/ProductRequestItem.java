package za.co.trademesh.modules.procurement.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductRequestItem(
        UUID id, String productCode, String description, BigDecimal quantity, UnitOfMeasure unitOfMeasure) {}
