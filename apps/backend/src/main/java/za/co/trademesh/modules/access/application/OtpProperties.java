package za.co.trademesh.modules.access.application;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.access.otp")
public record OtpProperties(
        String provider,
        URI baseUrl,
        String accountSid,
        String authToken,
        String verifyServiceSid,
        String localCode,
        Duration sendCooldown) {

    public OtpProperties {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("OTP provider is required");
        }
        if (baseUrl == null || !baseUrl.isAbsolute()) {
            throw new IllegalArgumentException("OTP base URL must be absolute");
        }
        if (sendCooldown == null || sendCooldown.isZero() || sendCooldown.isNegative()) {
            throw new IllegalArgumentException("OTP send cooldown must be positive");
        }
    }
}
