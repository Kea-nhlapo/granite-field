package za.co.trademesh.modules.access.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import za.co.trademesh.modules.access.domain.RegistrationType;
import za.co.trademesh.shared.security.AccountRole;
import za.co.trademesh.shared.security.AuthorizationService;
import za.co.trademesh.support.PostgresIntegrationTest;

class AuthServiceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthorizationService authorizationService;

    @BeforeEach
    void cleanAccessTables() {
        jdbcTemplate.update("DELETE FROM access_refresh_session");
        jdbcTemplate.update("DELETE FROM access_business_membership");
        jdbcTemplate.update("DELETE FROM business_registered_onboarding");
        jdbcTemplate.update("DELETE FROM business_profile");
        jdbcTemplate.update("DELETE FROM access_user_role");
        jdbcTemplate.update("DELETE FROM access_user_account");
    }

    @Test
    void registrationNormalizesEmailHashesPasswordAndIssuesTokens() {
        AuthService.AuthTokens tokens =
                authService.register("  Owner@Example.COM ", "correct-horse-battery", RegistrationType.BUSINESS_OWNER);

        String storedEmail = jdbcTemplate.queryForObject(
                "SELECT email FROM access_user_account WHERE id = ?", String.class, tokens.userId());
        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM access_user_account WHERE id = ?", String.class, tokens.userId());

        assertThat(storedEmail).isEqualTo("owner@example.com");
        assertThat(passwordHash).startsWith("{bcrypt}").doesNotContain("correct-horse-battery");
        assertThat(tokens.roles()).containsExactly(AccountRole.BUSINESS_OWNER);
        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
    }

    @Test
    void refreshTokenRotatesAndCannotBeUsedTwice() {
        AuthService.AuthTokens registered =
                authService.register("supplier@example.com", "correct-horse-battery", RegistrationType.SUPPLIER);

        AuthService.AuthTokens refreshed = authService.refresh(registered.refreshToken());

        assertThat(refreshed.refreshToken()).isNotEqualTo(registered.refreshToken());
        assertThatThrownBy(() -> authService.refresh(registered.refreshToken()))
                .isInstanceOf(AccessException.class)
                .extracting(exception -> ((AccessException) exception).code())
                .isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    void logoutRevokesTheRefreshTokenAndIsIdempotent() {
        AuthService.AuthTokens registered =
                authService.register("transporter@example.com", "correct-horse-battery", RegistrationType.TRANSPORTER);

        authService.logout(registered.refreshToken());
        authService.logout(registered.refreshToken());

        assertThatThrownBy(() -> authService.refresh(registered.refreshToken())).isInstanceOf(AccessException.class);
    }

    @Test
    void databaseMembershipNeverGrantsAccessToAnotherBusiness() {
        AuthService.AuthTokens owner =
                authService.register("owner@example.com", "correct-horse-battery", RegistrationType.BUSINESS_OWNER);
        UUID ownBusinessId = UUID.randomUUID();
        UUID otherBusinessId = UUID.randomUUID();
        insertBusiness(ownBusinessId, owner.userId(), "2024/000001/07");
        insertBusiness(otherBusinessId, owner.userId(), "2024/000002/07");
        jdbcTemplate.update("""
            INSERT INTO access_business_membership (
                business_id, user_id, membership_role, created_at
            ) VALUES (?, ?, 'BUSINESS_OWNER', CURRENT_TIMESTAMP)
            """, ownBusinessId, owner.userId());

        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                owner.userId().toString(), "not-used", List.of(new SimpleGrantedAuthority("ROLE_BUSINESS_OWNER")));

        assertThatCode(() -> authorizationService.requireBusinessAccess(authentication, ownBusinessId))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> authorizationService.requireBusinessAccess(authentication, otherBusinessId))
                .isInstanceOf(AccessDeniedException.class);
    }

    private void insertBusiness(UUID businessId, UUID ownerId, String registrationNumber) {
        jdbcTemplate.update("""
            INSERT INTO business_profile (
                id, registration_number, legal_name, registered_address,
                verification_status, lifecycle_status, confirmed_by_user_id, created_at
            ) VALUES (?, ?, 'Test Business', 'Test Address',
                      'REGISTRY_VERIFIED', 'ACTIVE', ?, CURRENT_TIMESTAMP)
            """, businessId, registrationNumber, ownerId);
    }
}
