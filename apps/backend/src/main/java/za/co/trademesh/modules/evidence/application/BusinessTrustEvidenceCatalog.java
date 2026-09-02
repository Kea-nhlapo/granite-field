package za.co.trademesh.modules.evidence.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Completed shipment outcomes derived only from the append-only evidence ledger. */
public interface BusinessTrustEvidenceCatalog {

    CompletionStats completionStats(UUID businessId);

    record CompletionStats(int completedTransactions, int successfulDeliveries, long sourceThroughSequence) {}

    default ScoreHistory scoreHistory(UUID businessId) {
        return new ScoreHistory(List.of(), 0);
    }

    record ScoreHistory(List<ScoreEvent> events, long sourceThroughSequence) {
        public ScoreHistory {
            events = List.copyOf(events);
        }
    }

    record ScoreEvent(String type, String outcome, Instant occurredAt, long ledgerSequence) {}
}
