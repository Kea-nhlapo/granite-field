package za.co.trademesh.modules.access.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshSessionRepository {
    void save(RefreshSession session);

    Optional<RefreshSession> findActiveByTokenHash(String tokenHash, Instant now);

    boolean revokeAndReplace(UUID currentSessionId, UUID replacementSessionId, Instant revokedAt);

    void revokeByTokenHash(String tokenHash, Instant revokedAt);
}
