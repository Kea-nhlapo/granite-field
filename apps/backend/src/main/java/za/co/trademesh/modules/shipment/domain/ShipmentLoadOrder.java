package za.co.trademesh.modules.shipment.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ShipmentLoadOrder(
        int sequence,
        UUID orderId,
        UUID buyerBusinessId,
        String destinationLabel,
        double latitude,
        double longitude,
        Instant deliveryWindowStart,
        Instant deliveryWindowEnd,
        List<ShipmentCargoItem> cargoItems) {

    public ShipmentLoadOrder {
        cargoItems = List.copyOf(cargoItems);
    }
}
