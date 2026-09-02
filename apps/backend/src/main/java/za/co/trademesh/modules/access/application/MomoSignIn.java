package za.co.trademesh.modules.access.application;

import java.time.Instant;
import java.util.UUID;
import za.co.trademesh.modules.payment.application.MomoClient;

public record MomoSignIn(
        UUID id,
        String pollTokenHash,
        String phoneNumber,
        String providerReference,
        MomoClient.ConsentStatus status,
        Instant expiresAt,
        Instant completedAt,
        Instant createdAt) {

    public boolean availableAt(Instant now) {
        return completedAt == null && expiresAt.isAfter(now);
    }
}
