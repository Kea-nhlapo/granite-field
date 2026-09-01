package za.co.trademesh.modules.access.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.access.domain.RefreshSession;
import za.co.trademesh.modules.access.domain.RefreshSessionRepository;
import za.co.trademesh.modules.access.domain.RegistrationType;
import za.co.trademesh.modules.access.domain.UserAccount;
import za.co.trademesh.modules.access.domain.UserAccountRepository;
import za.co.trademesh.shared.security.AccountRole;
import za.co.trademesh.shared.security.JwtProperties;
import za.co.trademesh.shared.security.JwtTokenService;
import za.co.trademesh.shared.security.SecureRefreshTokenService;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    private static final int MINIMUM_PASSWORD_BYTES = 12;
    private static final int MAXIMUM_BCRYPT_PASSWORD_BYTES = 72;

    private final UserAccountRepository accounts;
    private final RefreshSessionRepository refreshSessions;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokens;
    private final SecureRefreshTokenService refreshTokens;
    private final JwtProperties properties;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(
        UserAccountRepository accounts,
        RefreshSessionRepository refreshSessions,
        PasswordEncoder passwordEncoder,
        JwtTokenService jwtTokens,
        SecureRefreshTokenService refreshTokens,
        JwtProperties properties,
        Clock clock
    ) {
        this.accounts = accounts;
        this.refreshSessions = refreshSessions;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokens = jwtTokens;
        this.refreshTokens = refreshTokens;
        this.properties = properties;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("timing-only-password-value");
    }

    @Transactional
    public AuthTokens register(String email, String password, RegistrationType registrationType) {
        String normalizedEmail = normalizeEmail(email);
        validatePassword(password);
        if (accounts.emailExists(normalizedEmail)) {
            throw AccessException.emailAlreadyRegistered();
        }

        Instant now = clock.instant();
        UserAccount account = new UserAccount(
            UUID.randomUUID(),
            normalizedEmail,
            passwordEncoder.encode(password),
            true,
            now,
            Set.of(registrationType.accountRole()));

        try {
            accounts.save(account);
        } catch (DataIntegrityViolationException duplicateEmailRace) {
            throw AccessException.emailAlreadyRegistered();
        }

        return issueTokenPair(account, now);
    }

    @Transactional
    public AuthTokens login(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        var accountResult = accounts.findByEmail(normalizedEmail);
        String passwordHash = accountResult.map(UserAccount::passwordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(password, passwordHash);
        if (accountResult.isEmpty() || !accountResult.get().enabled() || !passwordMatches) {
            throw AccessException.invalidCredentials();
        }

        UserAccount account = accountResult.get();
        return issueTokenPair(account, clock.instant());
    }

    @Transactional
    public AuthTokens refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        String tokenHash = refreshTokens.hash(rawRefreshToken);
        RefreshSession current = refreshSessions.findActiveByTokenHash(tokenHash, now)
            .orElseThrow(AccessException::invalidRefreshToken);

        UserAccount account = accounts.findById(current.userId())
            .filter(UserAccount::enabled)
            .orElseThrow(AccessException::invalidRefreshToken);

        String replacementToken = refreshTokens.create();
        RefreshSession replacement = new RefreshSession(
            UUID.randomUUID(),
            account.id(),
            refreshTokens.hash(replacementToken),
            now.plus(properties.refreshTokenTtl()),
            now);
        refreshSessions.save(replacement);

        if (!refreshSessions.revokeAndReplace(current.id(), replacement.id(), now)) {
            // A concurrent request already used this token. The transaction rolls
            // back, including the unused replacement session inserted above.
            throw AccessException.invalidRefreshToken();
        }

        return createResponse(account, replacementToken);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshSessions.revokeByTokenHash(refreshTokens.hash(rawRefreshToken), clock.instant());
    }

    private AuthTokens issueTokenPair(UserAccount account, Instant now) {
        String refreshToken = refreshTokens.create();
        refreshSessions.save(new RefreshSession(
            UUID.randomUUID(),
            account.id(),
            refreshTokens.hash(refreshToken),
            now.plus(properties.refreshTokenTtl()),
            now));
        return createResponse(account, refreshToken);
    }

    private AuthTokens createResponse(UserAccount account, String refreshToken) {
        JwtTokenService.AccessToken accessToken = jwtTokens.issue(account.id(), account.roles());
        return new AuthTokens(
            account.id(),
            "Bearer",
            accessToken.value(),
            properties.accessTokenTtl().toSeconds(),
            refreshToken,
            account.roles());
    }

    private static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static void validatePassword(String password) {
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < MINIMUM_PASSWORD_BYTES || bytes > MAXIMUM_BCRYPT_PASSWORD_BYTES) {
            throw AccessException.invalidPassword();
        }
    }

    public record AuthTokens(
        UUID userId,
        String tokenType,
        String accessToken,
        long expiresInSeconds,
        String refreshToken,
        Set<AccountRole> roles
    ) {
    }
}
