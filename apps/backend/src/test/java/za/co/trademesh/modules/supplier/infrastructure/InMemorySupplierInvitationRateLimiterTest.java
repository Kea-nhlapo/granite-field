package za.co.trademesh.modules.supplier.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import za.co.trademesh.modules.supplier.application.SupplierInvitationProperties;

class InMemorySupplierInvitationRateLimiterTest {

    @Test
    void limitsEachClientAndAllowsItAgainAfterTheWindow() {
        var properties = new SupplierInvitationProperties(Duration.ofDays(7), 2, Duration.ofMinutes(1), 10);
        var limiter = new InMemorySupplierInvitationRateLimiter(properties);
        Instant now = Instant.parse("2026-09-02T00:00:00Z");

        assertThat(limiter.allow("client-a", now)).isTrue();
        assertThat(limiter.allow("client-a", now.plusSeconds(1))).isTrue();
        assertThat(limiter.allow("client-a", now.plusSeconds(2))).isFalse();
        assertThat(limiter.allow("client-b", now.plusSeconds(2))).isTrue();
        assertThat(limiter.allow("client-a", now.plusSeconds(61))).isTrue();
    }

    @Test
    void failsClosedWhenTheBoundedClientMapIsFull() {
        var properties = new SupplierInvitationProperties(Duration.ofDays(7), 2, Duration.ofMinutes(1), 1);
        var limiter = new InMemorySupplierInvitationRateLimiter(properties);
        Instant now = Instant.parse("2026-09-02T00:00:00Z");

        assertThat(limiter.allow("client-a", now)).isTrue();
        assertThat(limiter.allow("client-b", now)).isFalse();
    }
}
