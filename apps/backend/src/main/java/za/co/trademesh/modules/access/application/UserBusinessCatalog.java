package za.co.trademesh.modules.access.application;

import java.util.Optional;
import java.util.UUID;

/** Resolves the primary active business for a user without exposing membership storage. */
public interface UserBusinessCatalog {

    Optional<UUID> findPrimaryBusinessId(UUID userId);
}
