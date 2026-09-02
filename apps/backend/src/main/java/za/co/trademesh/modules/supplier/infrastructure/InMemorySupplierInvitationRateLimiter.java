package za.co.trademesh.modules.supplier.infrastructure;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.supplier.application.SupplierInvitationProperties;
import za.co.trademesh.modules.supplier.application.SupplierInvitationRateLimiter;

@Component
class InMemorySupplierInvitationRateLimiter implements SupplierInvitationRateLimiter {

    private final SupplierInvitationProperties properties;
    private final Map<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    InMemorySupplierInvitationRateLimiter(SupplierInvitationProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean allow(String clientKey, Instant now) {
        String key = clientKey == null || clientKey.isBlank() ? "unknown" : clientKey;
        discardExpiredWindows(now);
        if (!attempts.containsKey(key) && attempts.size() >= properties.maxTrackedClients()) {
            return false;
        }

        AttemptWindow window = attempts.compute(key, (ignored, current) -> {
            if (current == null || !current.windowEndsAt().isAfter(now)) {
                return new AttemptWindow(1, now.plus(properties.rateLimitWindow()));
            }
            return new AttemptWindow(current.count() + 1, current.windowEndsAt());
        });
        return window.count() <= properties.validationAttempts();
    }

    private void discardExpiredWindows(Instant now) {
        attempts.entrySet().removeIf(entry -> !entry.getValue().windowEndsAt().isAfter(now));
    }

    private record AttemptWindow(int count, Instant windowEndsAt) {}
}
