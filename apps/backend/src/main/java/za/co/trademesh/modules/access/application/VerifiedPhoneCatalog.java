package za.co.trademesh.modules.access.application;

import java.util.Optional;
import java.util.UUID;

/** Verified phone identities exposed without leaking authentication internals. */
public interface VerifiedPhoneCatalog {

    Optional<String> findPrimaryForBusiness(UUID businessId);
}
