package za.co.trademesh.modules.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.trademesh.modules.access.application.VerifiedPhoneCatalog;
import za.co.trademesh.modules.delivery.application.AcceptedDeliveryCatalog;
import za.co.trademesh.modules.procurement.application.ShipmentOrderCatalog;
import za.co.trademesh.modules.shipment.application.ShipmentEscrowCatalog;

class EscrowContextResolverTest {

    @Test
    void sumsOnlyThePayingBusinessConfirmedOrders() {
        UUID proposalId = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID firstOrderId = UUID.randomUUID();
        UUID secondOrderId = UUID.randomUUID();
        AcceptedDeliveryCatalog deliveries = mock(AcceptedDeliveryCatalog.class);
        ShipmentEscrowCatalog shipments = mock(ShipmentEscrowCatalog.class);
        ShipmentOrderCatalog orders = mock(ShipmentOrderCatalog.class);
        VerifiedPhoneCatalog phones = mock(VerifiedPhoneCatalog.class);
        when(deliveries.find(proposalId))
                .thenReturn(Optional.of(new AcceptedDeliveryCatalog.AcceptedDelivery(
                        proposalId, shipmentId, businessId, "+27825550100")));
        when(shipments.find(businessId, shipmentId))
                .thenReturn(Optional.of(new ShipmentEscrowCatalog.ShipmentEscrow(
                        shipmentId, businessId, List.of(firstOrderId, secondOrderId), false, false)));
        when(orders.find(businessId, firstOrderId))
                .thenReturn(Optional.of(order(firstOrderId, businessId, supplierId, "125.50")));
        when(orders.find(businessId, secondOrderId))
                .thenReturn(Optional.of(order(secondOrderId, businessId, supplierId, "74.50")));
        when(phones.findPrimaryForBusiness(businessId)).thenReturn(Optional.of("+27825550200"));

        var context = new EscrowContextResolver(deliveries, shipments, orders, phones, momo())
                .resolve(proposalId, shipmentId, businessId);

        assertThat(context.amount()).isEqualByComparingTo("200.00");
        assertThat(context.payerPhone()).isEqualTo("+27825550200");
        assertThat(context.supplierPhone()).isEqualTo("+27825550100");
        assertThat(context.supplierProfileId()).isEqualTo(supplierId);
    }

    private static ShipmentOrderCatalog.OrderSnapshot order(
            UUID orderId, UUID businessId, UUID supplierId, String amount) {
        BigDecimal total = new BigDecimal(amount);
        return new ShipmentOrderCatalog.OrderSnapshot(
                orderId,
                businessId,
                supplierId,
                UUID.randomUUID(),
                "ZAR",
                total,
                BigDecimal.ZERO,
                total,
                List.of(),
                Instant.parse("2026-09-02T12:00:00Z"));
    }

    private static MomoProperties momo() {
        var credentials = new MomoProperties.ProductCredentials("", "", "");
        return new MomoProperties(
                "mock",
                URI.create("https://momo.test"),
                "sandbox",
                null,
                "ZAR",
                Duration.ofSeconds(30),
                credentials,
                credentials);
    }
}
