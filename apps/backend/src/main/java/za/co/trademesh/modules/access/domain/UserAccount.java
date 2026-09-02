package za.co.trademesh.modules.access.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import za.co.trademesh.shared.security.AccountRole;

public record UserAccount(
        UUID id, String email, String passwordHash, boolean enabled, Instant createdAt, Set<AccountRole> roles) {
    public UserAccount {
        if ((email == null) != (passwordHash == null)) {
            throw new IllegalArgumentException(
                    "Email and password credentials must either both exist or both be absent");
        }
        roles = Set.copyOf(roles);
    }

    public boolean hasPasswordCredentials() {
        return email != null;
    }
}
