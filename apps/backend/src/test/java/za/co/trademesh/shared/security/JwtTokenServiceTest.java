package za.co.trademesh.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    @Test
    void accessTokenIsSignedShortLivedAndCarriesRoles() {
        JwtProperties properties = new JwtProperties(
            "test-only-auth-secret-32-characters",
            "https://trademesh.test",
            Duration.ofMinutes(15),
            Duration.ofDays(30));
        SecurityConfiguration configuration = new SecurityConfiguration();
        JwtEncoder encoder = configuration.jwtEncoder(properties);
        JwtDecoder decoder = configuration.jwtDecoder(properties);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        UUID userId = UUID.randomUUID();

        JwtTokenService service = new JwtTokenService(
            encoder, properties, Clock.fixed(now, ZoneOffset.UTC));
        JwtTokenService.AccessToken token = service.issue(
            userId, Set.of(AccountRole.SUPPLIER, AccountRole.BUSINESS_OWNER));

        Jwt decoded = decoder.decode(token.value());
        assertThat(decoded.getSubject()).isEqualTo(userId.toString());
        assertThat(decoded.getIssuer().toString()).isEqualTo("https://trademesh.test");
        assertThat(decoded.getClaimAsStringList("roles"))
            .containsExactly("BUSINESS_OWNER", "SUPPLIER");
        assertThat(token.expiresAt()).isEqualTo(now.plus(Duration.ofMinutes(15)));
    }
}
