package za.co.trademesh.modules.access.domain;

import java.time.Instant;
import java.util.UUID;

public interface BusinessMembershipRepository {
    void grantOwner(UUID businessId, UUID userId, Instant createdAt);
}
