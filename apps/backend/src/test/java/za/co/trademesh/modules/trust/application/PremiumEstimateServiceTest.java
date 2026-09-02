package za.co.trademesh.modules.trust.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.trademesh.modules.procurement.application.ShipmentOrderCatalog;
import za.co.trademesh.modules.shipment.application.ShipmentEscrowCatalog;
import za.co.trademesh.modules.shipment.application.ShipmentTrustCatalog;
import za.co.trademesh.modules.trust.domain.TrustScoreSnapshot;

class PremiumEstimateServiceTest {

    private static final UUID SHIPMENT_ID = UUID.fromString("77000000-0000-0000-0000-000000000011");
    private static final UUID BUSINESS_ID = UUID.fromString("77000000-0000-0000-0000-000000000012");
    private static final UUID ORDER_ID = UUID.fromString("77000000-0000-0000-0000-000000000013");
    private static final Instant NOW = Instant.parse("2026-09-03T19:00:00Z");

    @Test
    void reducesTheEstimateWhenVerifiedTrustImproves() {
        TrustScoreService trust = mock(TrustScoreService.class);
        when(trust.getForBusiness(BUSINESS_ID)).thenReturn(score("50.00"), score("80.00"));
        ShipmentTrustCatalog shipments = shipmentId -> Optional.of(BUSINESS_ID);
        ShipmentEscrowCatalog shipmentOrders = (businessId, shipmentId) -> Optional.of(
                new ShipmentEscrowCatalog.ShipmentEscrow(SHIPMENT_ID, BUSINESS_ID, List.of(ORDER_ID), false, false));
        ShipmentOrderCatalog orders = (businessId, orderId) -> Optional.of(new ShipmentOrderCatalog.OrderSnapshot(
                ORDER_ID,
                BUSINESS_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ZAR",
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                new BigDecimal("10000.00"),
                List.of(),
                NOW));
        PremiumEstimateService service =
                new PremiumEstimateService(shipments, shipmentOrders, orders, trust, Clock.fixed(NOW, ZoneOffset.UTC));

        var before = service.estimate(SHIPMENT_ID);
        var after = service.estimate(SHIPMENT_ID);

        assertThat(before.platformPremium()).isEqualByComparingTo("290.00");
        assertThat(after.platformPremium()).isEqualByComparingTo("224.00");
        assertThat(after.platformPremium()).isLessThan(before.platformPremium());
        assertThat(after.genericInsurerPremium()).isEqualByComparingTo("450.00");
        assertThat(after.status()).isEqualTo("ESTIMATE_ONLY");
    }

    private static TrustScoreSnapshot score(String verified) {
        return new TrustScoreSnapshot(
                BUSINESS_ID,
                new BigDecimal(verified),
                new BigDecimal(verified),
                "COMPRESSED_DEMO",
                "trust-score/v1",
                0,
                NOW,
                NOW,
                NOW.plusSeconds(30));
    }
}
