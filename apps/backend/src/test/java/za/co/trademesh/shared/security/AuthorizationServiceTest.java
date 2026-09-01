package za.co.trademesh.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OWN_BUSINESS_ID = UUID.randomUUID();
    private static final UUID OTHER_BUSINESS_ID = UUID.randomUUID();

    private final AuthorizationService authorization = new AuthorizationService(
        (userId, businessId) -> USER_ID.equals(userId) && OWN_BUSINESS_ID.equals(businessId));

    @Test
    void businessMemberCanAccessOnlyTheirOwnBusiness() {
        Authentication member = authentication(AccountRole.BUSINESS_MEMBER);

        assertThatCode(() -> authorization.requireBusinessAccess(member, OWN_BUSINESS_ID))
            .doesNotThrowAnyException();

        assertThatThrownBy(() -> authorization.requireBusinessAccess(member, OTHER_BUSINESS_ID))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ordinaryBusinessUsersCannotReadInternalRiskOrInsurerData() {
        Authentication owner = authentication(AccountRole.BUSINESS_OWNER);

        assertThatThrownBy(() -> authorization.requireInternalRiskAccess(owner))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> authorization.requireInsurerAccess(owner))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void sensitiveRolesRemainSeparate() {
        assertThatCode(() -> authorization.requireInternalRiskAccess(
            authentication(AccountRole.INTERNAL_RISK_ANALYST))).doesNotThrowAnyException();
        assertThatCode(() -> authorization.requireInsurerAccess(
            authentication(AccountRole.INSURER))).doesNotThrowAnyException();

        assertThatThrownBy(() -> authorization.requireInsurerAccess(
            authentication(AccountRole.INTERNAL_RISK_ANALYST)))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void administratorCanCrossTenantBoundaries() {
        assertThatCode(() -> authorization.requireBusinessAccess(
            authentication(AccountRole.ADMINISTRATOR), OTHER_BUSINESS_ID))
            .doesNotThrowAnyException();
    }

    private static Authentication authentication(AccountRole role) {
        return UsernamePasswordAuthenticationToken.authenticated(
            USER_ID.toString(),
            "not-used",
            List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }
}
