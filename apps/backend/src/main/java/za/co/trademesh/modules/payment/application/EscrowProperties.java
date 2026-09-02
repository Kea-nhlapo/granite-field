package za.co.trademesh.modules.payment.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.payments.escrow")
public record EscrowProperties(Duration pollInterval, Duration transactionTimeout, Duration streamTimeout) {

    public EscrowProperties {
        if (pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()) {
            throw new IllegalArgumentException("Escrow poll interval must be positive");
        }
        if (transactionTimeout == null
                || transactionTimeout.isZero()
                || transactionTimeout.isNegative()
                || transactionTimeout.compareTo(pollInterval) <= 0) {
            throw new IllegalArgumentException("Escrow transaction timeout must exceed its poll interval");
        }
        if (streamTimeout == null || streamTimeout.isZero() || streamTimeout.isNegative()) {
            throw new IllegalArgumentException("Escrow stream timeout must be positive");
        }
    }
}
