package za.co.trademesh.modules.access.infrastructure;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.access.application.OtpProperties;
import za.co.trademesh.modules.access.application.OtpProvider;

@Component
@ConditionalOnProperty(prefix = "trademesh.access.otp", name = "provider", havingValue = "local")
class LocalOtpProvider implements OtpProvider {

    private final String expectedCode;
    private final Set<String> pending = ConcurrentHashMap.newKeySet();

    LocalOtpProvider(OtpProperties properties) {
        if (properties.localCode() == null || !properties.localCode().matches("[0-9]{4,10}")) {
            throw new IllegalStateException("Local OTP code must contain 4 to 10 digits");
        }
        this.expectedCode = properties.localCode();
    }

    @Override
    public void send(String phoneNumber) {
        pending.add(phoneNumber);
    }

    @Override
    public boolean verify(String phoneNumber, String code) {
        return expectedCode.equals(code) && pending.remove(phoneNumber);
    }
}
