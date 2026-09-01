package za.co.trademesh.shared.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.security.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {
    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("AUTH_JWT_SECRET must contain at least 32 bytes");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("AUTH_JWT_ISSUER must not be blank");
        }
        requirePositive(accessTokenTtl, "AUTH_ACCESS_TOKEN_TTL");
        requirePositive(refreshTokenTtl, "AUTH_REFRESH_TOKEN_TTL");
    }

    private static void requirePositive(Duration value, String setting) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(setting + " must be positive");
        }
    }
}
