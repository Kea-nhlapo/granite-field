package za.co.trademesh.modules.procurement.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only order facts exposed to modules that build logistics plans. */
public interface AggregationOrderCatalog {

    Optional<OrderCandidate> findConfirmedOrder(UUID buyerBusinessId, UUID orderId);

    List<OrderCandidate> findNearbyConfirmedOrders(UUID anchorOrderId, double searchRadiusMeters, int limit);

    record OrderCandidate(
            UUID orderId,
            UUID buyerBusinessId,
            UUID supplierProfileId,
            String destinationLabel,
            double destinationLatitude,
            double destinationLongitude,
            double distanceFromAnchorMeters,
            Instant deliveryWindowStart,
            Instant deliveryWindowEnd,
            List<CargoItem> cargoItems) {

        public OrderCandidate {
            cargoItems = List.copyOf(cargoItems);
        }
    }

    record CargoItem(String productCode, String unitOfMeasure) {}
}
