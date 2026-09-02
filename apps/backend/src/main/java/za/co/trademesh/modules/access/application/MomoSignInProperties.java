package za.co.trademesh.modules.access.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.access.momo-sign-in")
public record MomoSignInProperties(Duration pollTokenTtl) {

    public MomoSignInProperties {
        if (pollTokenTtl == null || pollTokenTtl.isZero() || pollTokenTtl.isNegative()) {
            throw new IllegalArgumentException("MoMo sign-in poll token TTL must be positive");
        }
    }
}
