package za.co.trademesh.modules.evidence.application;

import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import za.co.trademesh.shared.events.PublishedEvent;

@Component
class EvidenceDomainEventListener {

    private final List<EvidenceProjector> projectors;
    private final EvidenceLedger ledger;

    EvidenceDomainEventListener(List<EvidenceProjector> projectors, EvidenceLedger ledger) {
        this.projectors = List.copyOf(projectors);
        this.ledger = ledger;
    }

    /**
     * Evidence is the durable local history, so it joins the publishing transaction.
     * A failed evidence write rolls the business change back instead of leaving a gap.
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void capture(PublishedEvent<?> published) {
        List<EvidenceProjector> matches = projectors.stream()
                .filter(projector -> projector.supports(published.event()))
                .toList();
        if (matches.isEmpty()) {
            return;
        }
        if (matches.size() != 1) {
            throw new IllegalStateException("Exactly one evidence projector must support "
                    + published.envelope().type());
        }
        ledger.record(published, matches.getFirst().project(published.event()));
    }
}
