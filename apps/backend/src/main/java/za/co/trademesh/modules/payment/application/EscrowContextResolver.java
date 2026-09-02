package za.co.trademesh.modules.payment.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.access.application.VerifiedPhoneCatalog;
import za.co.trademesh.modules.delivery.application.AcceptedDeliveryCatalog;
import za.co.trademesh.modules.procurement.application.ShipmentOrderCatalog;
import za.co.trademesh.modules.shipment.application.ShipmentEscrowCatalog;

@Component
class EscrowContextResolver {

    private final AcceptedDeliveryCatalog deliveries;
    private final ShipmentEscrowCatalog shipments;
    private final ShipmentOrderCatalog orders;
    private final VerifiedPhoneCatalog verifiedPhones;
    private final MomoProperties momo;

    EscrowContextResolver(
            AcceptedDeliveryCatalog deliveries,
            ShipmentEscrowCatalog shipments,
            ShipmentOrderCatalog orders,
            VerifiedPhoneCatalog verifiedPhones,
            MomoProperties momo) {
        this.deliveries = deliveries;
        this.shipments = shipments;
        this.orders = orders;
        this.verifiedPhones = verifiedPhones;
        this.momo = momo;
    }

    LockContext resolve(UUID proposalId, UUID shipmentId, UUID businessId) {
        var delivery = deliveries
                .find(proposalId)
                .filter(value -> value.shipmentId().equals(shipmentId)
                        && value.businessId().equals(businessId))
                .orElseThrow(EscrowException::contextUnavailable);
        var shipment = shipments.find(businessId, shipmentId).orElseThrow(EscrowException::contextUnavailable);
        List<ShipmentOrderCatalog.OrderSnapshot> confirmedOrders = shipment.orderIds().stream()
                .map(orderId -> orders.find(businessId, orderId).orElseThrow(EscrowException::contextUnavailable))
                .toList();
        if (confirmedOrders.isEmpty()) {
            throw EscrowException.contextUnavailable();
        }
        UUID supplierProfileId = confirmedOrders.getFirst().supplierProfileId();
        String currency = confirmedOrders.getFirst().currency();
        boolean consistent = confirmedOrders.stream()
                .allMatch(order -> order.supplierProfileId().equals(supplierProfileId)
                        && order.currency().equals(currency));
        if (!consistent || !currency.equalsIgnoreCase(momo.currency())) {
            throw EscrowException.contextUnavailable();
        }
        BigDecimal amount = confirmedOrders.stream()
                .map(ShipmentOrderCatalog.OrderSnapshot::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.signum() <= 0) {
            throw EscrowException.contextUnavailable();
        }
        String payerPhone =
                verifiedPhones.findPrimaryForBusiness(businessId).orElseThrow(EscrowException::verifiedPayerRequired);
        return new LockContext(
                shipmentId,
                businessId,
                supplierProfileId,
                currency.toUpperCase(java.util.Locale.ROOT),
                amount,
                payerPhone,
                delivery.supplierPhone());
    }

    record LockContext(
            UUID shipmentId,
            UUID businessId,
            UUID supplierProfileId,
            String currency,
            BigDecimal amount,
            String payerPhone,
            String supplierPhone) {}
}
