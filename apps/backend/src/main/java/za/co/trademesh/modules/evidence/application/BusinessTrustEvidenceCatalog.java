package za.co.trademesh.modules.evidence.application;

import java.util.UUID;

/** Completed shipment outcomes derived only from the append-only evidence ledger. */
public interface BusinessTrustEvidenceCatalog {

    CompletionStats completionStats(UUID businessId);

    record CompletionStats(int completedTransactions, int successfulDeliveries, long sourceThroughSequence) {}
}
