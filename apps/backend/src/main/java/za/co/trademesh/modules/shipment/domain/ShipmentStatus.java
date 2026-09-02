package za.co.trademesh.modules.shipment.domain;

import java.util.EnumSet;
import java.util.Set;

public enum ShipmentStatus {
    AWAITING_COLLECTION,
    COLLECTED,
    IN_TRANSIT,
    DELAYED,
    DELIVERED,
    DISPUTED,
    CANCELLED;

    public boolean canTransitionTo(ShipmentStatus target) {
        return allowedTargets().contains(target);
    }

    private Set<ShipmentStatus> allowedTargets() {
        return switch (this) {
            case AWAITING_COLLECTION -> EnumSet.of(COLLECTED, CANCELLED);
            case COLLECTED -> EnumSet.of(IN_TRANSIT, CANCELLED, DISPUTED);
            case IN_TRANSIT -> EnumSet.of(DELAYED, DELIVERED, DISPUTED);
            case DELAYED -> EnumSet.of(IN_TRANSIT, DELIVERED, DISPUTED);
            case DELIVERED -> EnumSet.of(DISPUTED);
            case DISPUTED, CANCELLED -> EnumSet.noneOf(ShipmentStatus.class);
        };
    }
}
