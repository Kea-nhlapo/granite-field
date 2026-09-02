package za.co.trademesh.modules.procurement.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductRequest(
        UUID id,
        UUID buyerBusinessId,
        ProcurementRequestStatus status,
        String destinationLabel,
        double destinationLatitude,
        double destinationLongitude,
        Instant deliveryWindowStart,
        Instant deliveryWindowEnd,
        List<ProductRequestItem> items,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt) {

    public ProductRequest {
        items = List.copyOf(items);
    }
}
