package za.co.trademesh.modules.access.application;

import java.time.Duration;
import java.time.Instant;

public interface OtpSendRateLimiter {

    boolean acquire(String phoneNumber, Instant now, Duration cooldown);
}
