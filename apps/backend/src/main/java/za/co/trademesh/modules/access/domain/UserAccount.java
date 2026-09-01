package za.co.trademesh.modules.access.domain;

import za.co.trademesh.shared.security.AccountRole;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserAccount(
    UUID id,
    String email,
    String passwordHash,
    boolean enabled,
    Instant createdAt,
    Set<AccountRole> roles
) {
    public UserAccount {
        roles = Set.copyOf(roles);
    }
}
