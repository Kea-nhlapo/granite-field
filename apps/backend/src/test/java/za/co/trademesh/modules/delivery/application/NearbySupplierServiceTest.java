package za.co.trademesh.modules.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.trademesh.modules.supplier.application.SupplierDirectory;

class NearbySupplierServiceTest {

    @Test
    void ranksRegisteredSuppliersByMeasuredRoadDistance() {
        UUID farther = UUID.randomUUID();
        UUID nearer = UUID.randomUUID();
        SupplierDirectory directory = mock(SupplierDirectory.class);
        when(directory.listRegistered(10))
                .thenReturn(List.of(
                        supplier(farther, "Far Supplier", "Pretoria", "4.9"),
                        supplier(nearer, "Near Supplier", "Tembisa", "4.0")));
        SupplierDistanceClient distances = (latitude, longitude, destinations) -> Map.of(
                farther, new SupplierDistanceClient.Distance(42_000, 2_900),
                nearer, new SupplierDistanceClient.Distance(3_000, 360));
        var service = new NearbySupplierService(directory, distances);

        var result = service.find(-26.0, 28.2, 10);

        assertThat(result)
                .extracting(NearbySupplierService.NearbySupplier::supplierProfileId)
                .containsExactly(nearer, farther);
        assertThat(result.getFirst().durationSeconds()).isEqualTo(360);
    }

    @Test
    void failsClearlyWhenRoadDistancesCannotBeCalculated() {
        SupplierDirectory directory = mock(SupplierDirectory.class);
        when(directory.listRegistered(10))
                .thenReturn(List.of(supplier(UUID.randomUUID(), "Supplier", "Johannesburg", "4.2")));
        SupplierDistanceClient distances = (latitude, longitude, destinations) -> {
            throw new IllegalStateException("provider down");
        };
        var service = new NearbySupplierService(directory, distances);

        assertThatThrownBy(() -> service.find(-26.0, 28.2, 10))
                .isInstanceOf(DeliveryException.class)
                .extracting(failure -> ((DeliveryException) failure).code())
                .isEqualTo("SUPPLIER_DISTANCE_UNAVAILABLE");
    }

    private static SupplierDirectory.SearchResult supplier(
            UUID supplierId, String name, String address, String rating) {
        return new SupplierDirectory.SearchResult(
                supplierId, UUID.randomUUID(), name, address, new BigDecimal(rating), new BigDecimal("0.95"));
    }
}
