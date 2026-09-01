package za.co.trademesh.modules.access.domain;

import java.time.Instant;
import java.util.UUID;

public record RefreshSession(
    UUID id,
    UUID userId,
    String tokenHash,
    Instant expiresAt,
    Instant createdAt
) {
}
