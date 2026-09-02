package za.co.trademesh.modules.document.domain;

import java.time.Instant;

public record DocumentStateTransition(
        DocumentState fromState, DocumentState toState, String reason, String actor, Instant occurredAt) {}
