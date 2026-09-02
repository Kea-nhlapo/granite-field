package za.co.trademesh.modules.access.application;

import java.util.UUID;
import za.co.trademesh.shared.security.AccountRole;

/** Narrow account boundary for assigning protected partner work. */
public interface AccountRoleDirectory {

    boolean isActiveWithRole(UUID userId, AccountRole role);
}
