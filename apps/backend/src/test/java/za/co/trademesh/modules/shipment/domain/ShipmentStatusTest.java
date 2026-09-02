package za.co.trademesh.modules.shipment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ShipmentStatusTest {

    @Test
    void allowsOnlyExplicitLifecycleTransitions() {
        assertThat(ShipmentStatus.AWAITING_COLLECTION.canTransitionTo(ShipmentStatus.COLLECTED))
                .isTrue();
        assertThat(ShipmentStatus.AWAITING_COLLECTION.canTransitionTo(ShipmentStatus.CANCELLED))
                .isTrue();
        assertThat(ShipmentStatus.COLLECTED.canTransitionTo(ShipmentStatus.IN_TRANSIT))
                .isTrue();
        assertThat(ShipmentStatus.IN_TRANSIT.canTransitionTo(ShipmentStatus.DELAYED))
                .isTrue();
        assertThat(ShipmentStatus.DELAYED.canTransitionTo(ShipmentStatus.IN_TRANSIT))
                .isTrue();
        assertThat(ShipmentStatus.IN_TRANSIT.canTransitionTo(ShipmentStatus.DELIVERED))
                .isTrue();
        assertThat(ShipmentStatus.DELIVERED.canTransitionTo(ShipmentStatus.DISPUTED))
                .isTrue();

        assertThat(ShipmentStatus.AWAITING_COLLECTION.canTransitionTo(ShipmentStatus.DELIVERED))
                .isFalse();
        assertThat(ShipmentStatus.COLLECTED.canTransitionTo(ShipmentStatus.DELIVERED))
                .isFalse();
        assertThat(ShipmentStatus.DELIVERED.canTransitionTo(ShipmentStatus.IN_TRANSIT))
                .isFalse();
        assertThat(ShipmentStatus.DISPUTED.canTransitionTo(ShipmentStatus.DELIVERED))
                .isFalse();
        assertThat(ShipmentStatus.CANCELLED.canTransitionTo(ShipmentStatus.COLLECTED))
                .isFalse();
    }
}
