package za.co.trademesh.modules.trust.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrustScoreRepository {

    Optional<TrustScoreSnapshot> find(UUID businessId);

    void save(TrustScoreSnapshot snapshot);

    List<UUID> findDueBusinessIds(Instant dueAt, int limit);
}
