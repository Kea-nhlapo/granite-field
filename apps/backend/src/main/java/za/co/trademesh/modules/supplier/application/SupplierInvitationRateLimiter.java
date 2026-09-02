package za.co.trademesh.modules.supplier.application;

import java.time.Instant;

public interface SupplierInvitationRateLimiter {
    boolean allow(String clientKey, Instant now);
}
