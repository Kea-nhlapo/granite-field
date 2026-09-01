package za.co.trademesh.modules.access.domain;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository {
    Optional<UserAccount> findByEmail(String normalizedEmail);

    Optional<UserAccount> findById(UUID id);

    boolean emailExists(String normalizedEmail);

    void save(UserAccount account);
}
