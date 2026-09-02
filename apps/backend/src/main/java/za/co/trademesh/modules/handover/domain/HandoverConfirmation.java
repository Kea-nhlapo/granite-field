package za.co.trademesh.modules.handover.domain;

import java.time.Instant;
import java.util.UUID;

public record HandoverConfirmation(
        UUID id,
        UUID challengeId,
        UUID commandId,
        String inputFingerprint,
        UUID actorUserId,
        HandoverParty party,
        Instant observedAt,
        Instant receivedAt,
        double latitude,
        double longitude,
        double distanceMetres,
        QuantityOutcome quantityOutcome,
        String quantityNote) {}
