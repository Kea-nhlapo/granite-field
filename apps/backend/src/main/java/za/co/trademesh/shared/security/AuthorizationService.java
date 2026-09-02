package za.co.trademesh.shared.security;

import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service("authorizationService")
public class AuthorizationService {

    private final TenantMembershipLookup membershipLookup;

    public AuthorizationService(TenantMembershipLookup membershipLookup) {
        this.membershipLookup = membershipLookup;
    }

    public void requireBusinessAccess(Authentication authentication, UUID businessId) {
        requireAuthenticated(authentication);
        if (hasRole(authentication, AccountRole.ADMINISTRATOR)) {
            return;
        }

        UUID userId = authenticatedUserId(authentication);
        if (!membershipLookup.isMember(userId, businessId)) {
            throw new AccessDeniedException("This account cannot access the requested business");
        }
    }

    public void requireInternalRiskAccess(Authentication authentication) {
        requireAnyRole(authentication, AccountRole.INTERNAL_RISK_ANALYST, AccountRole.ADMINISTRATOR);
    }

    public void requireInsurerAccess(Authentication authentication) {
        requireAnyRole(authentication, AccountRole.INSURER, AccountRole.ADMINISTRATOR);
    }

    public void requireSelfOrAdministrator(Authentication authentication, UUID userId) {
        requireAuthenticated(authentication);
        if (hasRole(authentication, AccountRole.ADMINISTRATOR)) {
            return;
        }
        if (!authenticatedUserId(authentication).equals(userId)) {
            throw new AccessDeniedException("This account cannot access the requested user");
        }
    }

    public UUID authenticatedUserId(Authentication authentication) {
        requireAuthenticated(authentication);
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException invalidSubject) {
            throw new AccessDeniedException("Authenticated subject is invalid", invalidSubject);
        }
    }

    public boolean hasRole(Authentication authentication, AccountRole role) {
        return authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role.name()));
    }

    private void requireAnyRole(Authentication authentication, AccountRole... roles) {
        requireAuthenticated(authentication);
        for (AccountRole role : roles) {
            if (hasRole(authentication, role)) {
                return;
            }
        }
        throw new AccessDeniedException("This account does not have the required role");
    }

    private static void requireAuthenticated(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }
    }
}
