package za.co.trademesh.modules.access.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.access.otp")
public record OtpProperties(String provider, String localCode, Duration sendCooldown) {

    public OtpProperties {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("OTP provider is required");
        }
        if (sendCooldown == null || sendCooldown.isZero() || sendCooldown.isNegative()) {
            throw new IllegalArgumentException("OTP send cooldown must be positive");
        }
    }
}
