package za.co.trademesh.modules.procurement.application;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Authoritative order quantity exposed to delivery verification without leaking procurement internals. */
public interface DeliveryOrderQuantityCatalog {

    Optional<ExpectedQuantity> findExpectedQuantity(UUID buyerBusinessId, UUID orderId);

    record ExpectedQuantity(BigDecimal quantity, String unitOfMeasure) {}
}
