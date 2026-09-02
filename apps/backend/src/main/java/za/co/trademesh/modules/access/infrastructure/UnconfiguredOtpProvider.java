package za.co.trademesh.modules.access.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.access.application.OtpProvider;

/** Fallback OTP provider: the application starts, sending or verifying a code fails loudly. */
@Component
@ConditionalOnProperty(
        prefix = "trademesh.access.otp",
        name = "provider",
        havingValue = "unconfigured",
        matchIfMissing = true)
class UnconfiguredOtpProvider implements OtpProvider {

    @Override
    public void send(String phoneNumber) {
        throw new IllegalStateException("No OTP provider is configured; set trademesh.access.otp.provider");
    }

    @Override
    public boolean verify(String phoneNumber, String code) {
        throw new IllegalStateException("No OTP provider is configured; set trademesh.access.otp.provider");
    }
}
