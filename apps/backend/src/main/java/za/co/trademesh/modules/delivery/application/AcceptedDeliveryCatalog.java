package za.co.trademesh.modules.delivery.application;

import java.util.Optional;
import java.util.UUID;

/** Payment-safe view of an accepted delivery proposal. */
public interface AcceptedDeliveryCatalog {

    Optional<AcceptedDelivery> find(UUID proposalId);

    record AcceptedDelivery(UUID proposalId, UUID shipmentId, UUID businessId, String supplierPhone) {}
}
