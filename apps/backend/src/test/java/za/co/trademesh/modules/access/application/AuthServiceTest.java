package za.co.trademesh.modules.access.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import za.co.trademesh.modules.access.domain.RefreshSession;
import za.co.trademesh.modules.access.domain.RefreshSessionRepository;
import za.co.trademesh.modules.access.domain.RegistrationType;
import za.co.trademesh.modules.access.domain.UserAccount;
import za.co.trademesh.modules.access.domain.UserAccountRepository;
import za.co.trademesh.shared.security.AccountRole;
import za.co.trademesh.shared.security.JwtProperties;
import za.co.trademesh.shared.security.JwtTokenService;
import za.co.trademesh.shared.security.SecureRefreshTokenService;

class AuthServiceTest {

    private InMemoryAccounts accounts;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        JwtProperties properties = new JwtProperties(
                "test-only-auth-secret-32-characters",
                "https://trademesh.test",
                Duration.ofMinutes(15),
                Duration.ofDays(30));
        var key = new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = NimbusJwtEncoder.withSecretKey(key)
                .algorithm(MacAlgorithm.HS256)
                .build();
        accounts = new InMemoryAccounts();
        authService = new AuthService(
                accounts,
                new InMemoryRefreshSessions(),
                PasswordEncoderFactories.createDelegatingPasswordEncoder(),
                new JwtTokenService(encoder, properties, clock),
                new SecureRefreshTokenService(),
                properties,
                clock);
    }

    @Test
    void registrationHashesThePasswordAndCreatesOnlyTheChosenPublicRole() {
        AuthService.AuthTokens tokens =
                authService.register("Owner@Example.com", "correct-horse-battery", RegistrationType.BUSINESS_OWNER);

        UserAccount account = accounts.findById(tokens.userId()).orElseThrow();
        assertThat(account.email()).isEqualTo("owner@example.com");
        assertThat(account.passwordHash()).startsWith("{bcrypt}");
        assertThat(account.passwordHash()).doesNotContain("correct-horse-battery");
        assertThat(account.roles()).containsExactly(AccountRole.BUSINESS_OWNER);
    }

    @Test
    void refreshRotatesOnceAndLogoutRevokesTheReplacement() {
        AuthService.AuthTokens registered =
                authService.register("supplier@example.com", "correct-horse-battery", RegistrationType.SUPPLIER);

        AuthService.AuthTokens refreshed = authService.refresh(registered.refreshToken());
        assertThat(refreshed.refreshToken()).isNotEqualTo(registered.refreshToken());
        assertThatThrownBy(() -> authService.refresh(registered.refreshToken())).isInstanceOf(AccessException.class);

        authService.logout(refreshed.refreshToken());
        assertThatThrownBy(() -> authService.refresh(refreshed.refreshToken())).isInstanceOf(AccessException.class);
    }

    private static final class InMemoryAccounts implements UserAccountRepository {
        private final Map<UUID, UserAccount> byId = new HashMap<>();
        private final Map<String, UserAccount> byEmail = new HashMap<>();

        @Override
        public Optional<UserAccount> findByEmail(String normalizedEmail) {
            return Optional.ofNullable(byEmail.get(normalizedEmail));
        }

        @Override
        public Optional<UserAccount> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public boolean emailExists(String normalizedEmail) {
            return byEmail.containsKey(normalizedEmail);
        }

        @Override
        public void save(UserAccount account) {
            byId.put(account.id(), account);
            byEmail.put(account.email(), account);
        }
    }

    private static final class InMemoryRefreshSessions implements RefreshSessionRepository {
        private final Map<UUID, RefreshSession> byId = new HashMap<>();
        private final Map<String, UUID> byHash = new HashMap<>();
        private final Map<UUID, Instant> revokedAt = new HashMap<>();

        @Override
        public void save(RefreshSession session) {
            byId.put(session.id(), session);
            byHash.put(session.tokenHash(), session.id());
        }

        @Override
        public Optional<RefreshSession> findActiveByTokenHash(String tokenHash, Instant now) {
            UUID id = byHash.get(tokenHash);
            if (id == null || revokedAt.containsKey(id)) {
                return Optional.empty();
            }
            return Optional.ofNullable(byId.get(id))
                    .filter(session -> session.expiresAt().isAfter(now));
        }

        @Override
        public boolean revokeAndReplace(UUID currentSessionId, UUID replacementSessionId, Instant at) {
            if (revokedAt.containsKey(currentSessionId)) {
                return false;
            }
            revokedAt.put(currentSessionId, at);
            return true;
        }

        @Override
        public void revokeByTokenHash(String tokenHash, Instant at) {
            UUID id = byHash.get(tokenHash);
            if (id != null) {
                revokedAt.putIfAbsent(id, at);
            }
        }
    }
}
