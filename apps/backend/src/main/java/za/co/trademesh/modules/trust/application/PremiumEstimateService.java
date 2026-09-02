package za.co.trademesh.modules.trust.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.procurement.application.ShipmentOrderCatalog;
import za.co.trademesh.modules.shipment.application.ShipmentEscrowCatalog;
import za.co.trademesh.modules.shipment.application.ShipmentTrustCatalog;

@Service
public class PremiumEstimateService {

    public static final String CALCULATION_VERSION = "premium-estimate/v1";
    private static final BigDecimal GENERIC_RATE = new BigDecimal("0.0450");
    private static final BigDecimal PLATFORM_MINIMUM_RATE = new BigDecimal("0.0180");
    private static final BigDecimal PLATFORM_RISK_SPREAD = new BigDecimal("0.0220");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ShipmentTrustCatalog shipments;
    private final ShipmentEscrowCatalog shipmentOrders;
    private final ShipmentOrderCatalog orders;
    private final TrustScoreService trustScores;
    private final Clock clock;

    public PremiumEstimateService(
            ShipmentTrustCatalog shipments,
            ShipmentEscrowCatalog shipmentOrders,
            ShipmentOrderCatalog orders,
            TrustScoreService trustScores,
            Clock clock) {
        this.shipments = shipments;
        this.shipmentOrders = shipmentOrders;
        this.orders = orders;
        this.trustScores = trustScores;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UUID requireBusinessId(UUID shipmentId) {
        return shipments.findRequestingBusinessId(required(shipmentId)).orElseThrow(TrustException::premiumUnavailable);
    }

    @Transactional(readOnly = true)
    public PremiumEstimate estimate(UUID shipmentId) {
        UUID shipmentReference = required(shipmentId);
        UUID businessId = requireBusinessId(shipmentReference);
        var shipment =
                shipmentOrders.find(businessId, shipmentReference).orElseThrow(TrustException::premiumUnavailable);
        List<ShipmentOrderCatalog.OrderSnapshot> confirmedOrders = shipment.orderIds().stream()
                .map(orderId -> orders.find(businessId, orderId).orElseThrow(TrustException::premiumUnavailable))
                .toList();
        if (confirmedOrders.isEmpty()) {
            throw TrustException.premiumUnavailable();
        }
        String currency = confirmedOrders.getFirst().currency();
        if (currency == null
                || confirmedOrders.stream().anyMatch(order -> !currency.equalsIgnoreCase(order.currency()))) {
            throw TrustException.premiumUnavailable();
        }
        BigDecimal cargoValue = confirmedOrders.stream()
                .map(ShipmentOrderCatalog.OrderSnapshot::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (cargoValue.signum() <= 0) {
            throw TrustException.premiumUnavailable();
        }
        BigDecimal verifiedScore = trustScores.getForBusiness(businessId).verifiedScore();
        BigDecimal riskFactor = HUNDRED.subtract(verifiedScore).divide(HUNDRED, 6, RoundingMode.HALF_UP);
        BigDecimal platformRate = PLATFORM_MINIMUM_RATE
                .add(PLATFORM_RISK_SPREAD.multiply(riskFactor))
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal platformPremium = money(cargoValue.multiply(platformRate));
        BigDecimal genericPremium = money(cargoValue.multiply(GENERIC_RATE));
        return new PremiumEstimate(
                shipmentReference,
                businessId,
                money(cargoValue),
                currency.toUpperCase(java.util.Locale.ROOT),
                verifiedScore,
                platformRate,
                platformPremium,
                GENERIC_RATE,
                genericPremium,
                money(genericPremium.subtract(platformPremium)),
                "ESTIMATE_ONLY",
                CALCULATION_VERSION,
                clock.instant().truncatedTo(ChronoUnit.MICROS));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static UUID required(UUID value) {
        if (value == null) {
            throw TrustException.premiumUnavailable();
        }
        return value;
    }

    public record PremiumEstimate(
            UUID shipmentId,
            UUID businessId,
            BigDecimal cargoValue,
            String currency,
            BigDecimal verifiedTrustScore,
            BigDecimal platformRate,
            BigDecimal platformPremium,
            BigDecimal genericInsurerRate,
            BigDecimal genericInsurerPremium,
            BigDecimal estimatedSaving,
            String status,
            String calculationVersion,
            Instant estimatedAt) {}
}
