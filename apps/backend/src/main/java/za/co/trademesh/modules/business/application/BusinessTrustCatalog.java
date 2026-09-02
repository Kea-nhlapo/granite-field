package za.co.trademesh.modules.business.application;

import java.util.Optional;
import java.util.UUID;

/** Public-safe verification facts used to calculate a trust summary. */
public interface BusinessTrustCatalog {

    Optional<BusinessTrustFacts> find(UUID businessId);

    record BusinessTrustFacts(UUID businessId, boolean registryVerified, boolean identityVerified) {}
}
