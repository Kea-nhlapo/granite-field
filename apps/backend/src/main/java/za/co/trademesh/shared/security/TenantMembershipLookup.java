package za.co.trademesh.shared.security;

import java.util.UUID;

public interface TenantMembershipLookup {
    boolean isMember(UUID userId, UUID businessId);
}
