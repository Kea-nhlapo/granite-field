package za.co.trademesh.modules.trust.domain;

import java.util.Optional;
import java.util.UUID;

public interface TrustRepository {

    Optional<PublicTrustSummary> find(UUID businessId);

    void save(PublicTrustSummary summary);
}
