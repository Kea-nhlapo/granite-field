package za.co.trademesh.modules.evidence.application;

import za.co.trademesh.shared.events.DomainEvent;

/** Opt-in mapping from a domain event to a deliberately small, safe evidence fact. */
public interface EvidenceProjector {

    boolean supports(DomainEvent event);

    EvidenceProjection project(DomainEvent event);
}
