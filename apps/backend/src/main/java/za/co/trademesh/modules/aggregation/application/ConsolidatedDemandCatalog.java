package za.co.trademesh.modules.aggregation.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only consolidated demand exposed to logistics without exposing aggregation persistence. */
public interface ConsolidatedDemandCatalog {

    Optional<ConsolidatedDemand> findActive(UUID requestedByBusinessId, UUID suggestionId);

    record ConsolidatedDemand(
            UUID suggestionId, UUID requestedByBusinessId, UUID anchorOrderId, List<DeliveryStop> deliveryStops) {

        public ConsolidatedDemand {
            deliveryStops = List.copyOf(deliveryStops);
        }
    }

    record DeliveryStop(
            UUID orderId,
            UUID buyerBusinessId,
            String destinationLabel,
            double latitude,
            double longitude,
            Instant deliveryWindowStart,
            Instant deliveryWindowEnd,
            List<CargoItem> cargoItems) {

        public DeliveryStop {
            cargoItems = List.copyOf(cargoItems);
        }
    }

    record CargoItem(String productCode, String unitOfMeasure) {}
}
