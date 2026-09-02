package za.co.trademesh.modules.notification.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;

final class OperationalMobileNotificationTemplates {

    private OperationalMobileNotificationTemplates() {}

    static String backhaul(UUID shipmentId, int matchCount, long distanceMetres, BigDecimal trustScore) {
        String kilometres = BigDecimal.valueOf(distanceMetres)
                .divide(BigDecimal.valueOf(1_000), 1, RoundingMode.HALF_UP)
                .toPlainString();
        String trust = trustScore
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .toPlainString();
        return "TradeMesh found " + matchCount + " backhaul option" + (matchCount == 1 ? "" : "s")
                + " for shipment " + shortId(shipmentId) + ". Best pickup detour: " + kilometres
                + " km. Public trust score: " + trust + "/100.";
    }

    static String deliveryScan(UUID shipmentId, String outcome) {
        if ("DISPUTED".equals(outcome)) {
            return "TradeMesh delivery " + shortId(shipmentId)
                    + ": the scanned quantity did not match. Payment release is blocked until the discrepancy is resolved.";
        }
        return "TradeMesh delivery " + shortId(shipmentId) + ": QR scan passed and the captured quantity matched.";
    }

    static String escrowReleased(UUID shipmentId, BigDecimal amount, String currency) {
        String safeCurrency = currency == null ? "" : currency.strip().toUpperCase(Locale.ROOT);
        String safeAmount = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        return "TradeMesh shipment " + shortId(shipmentId) + ": payment of " + safeCurrency + " " + safeAmount
                + " was released.";
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}
