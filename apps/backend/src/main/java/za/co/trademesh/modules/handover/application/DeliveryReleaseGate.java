package za.co.trademesh.modules.handover.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Delivery evidence required before a business's escrow can be released. */
public interface DeliveryReleaseGate {

    boolean releaseAllowed(UUID businessId, UUID shipmentId, List<UUID> orderIds);

    Resolution resolve(UUID businessId, UUID shipmentId, UUID commandId, BigDecimal resolvedAmount, UUID actorUserId);

    record Resolution(UUID resolutionId, BigDecimal resolvedAmount) {}
}
