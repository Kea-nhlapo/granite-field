package za.co.trademesh.modules.payment.application;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.integrations.momo")
public record MomoProperties(
        String provider,
        URI baseUrl,
        String targetEnvironment,
        URI callbackHost,
        String currency,
        Duration tokenRefreshSkew,
        ProductCredentials collections,
        ProductCredentials disbursements) {

    public MomoProperties {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("MoMo provider is required");
        }
        if (baseUrl == null || !baseUrl.isAbsolute()) {
            throw new IllegalArgumentException("MoMo base URL must be absolute");
        }
        if (targetEnvironment == null || targetEnvironment.isBlank()) {
            throw new IllegalArgumentException("MoMo target environment is required");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("MoMo currency is required");
        }
        if (tokenRefreshSkew == null || tokenRefreshSkew.isNegative()) {
            throw new IllegalArgumentException("MoMo token refresh skew cannot be negative");
        }
    }

    public ProductCredentials credentials(MomoClient.Product product) {
        return product == MomoClient.Product.COLLECTIONS ? collections : disbursements;
    }

    public record ProductCredentials(String subscriptionKey, String apiUser, String apiKey) {
        public boolean configured() {
            return present(subscriptionKey) && present(apiUser) && present(apiKey);
        }

        private static boolean present(String value) {
            return value != null && !value.isBlank();
        }
    }
}
