package za.co.trademesh.modules.payment.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.payment.application.MomoClient;

/** Fallback MoMo client: the application starts, any payment call fails loudly. */
@Component
@ConditionalOnProperty(
        prefix = "trademesh.integrations.momo",
        name = "provider",
        havingValue = "unconfigured",
        matchIfMissing = true)
class UnconfiguredMomoClient implements MomoClient {

    private static IllegalStateException unconfigured() {
        return new IllegalStateException("No MoMo provider is configured; set trademesh.integrations.momo.provider");
    }

    @Override
    public AccessToken getToken(Product product) {
        throw unconfigured();
    }

    @Override
    public ConsentRequest bcAuthorize(String phoneNumber) {
        throw unconfigured();
    }

    @Override
    public ConsentStatus getConsentStatus(String providerReference) {
        throw unconfigured();
    }

    @Override
    public UserInfo getBasicUserInfo(String providerReference) {
        throw unconfigured();
    }

    @Override
    public boolean validateAccountHolder(String phoneNumber) {
        throw unconfigured();
    }

    @Override
    public String requestToPay(MoneyRequest request) {
        throw unconfigured();
    }

    @Override
    public String transfer(MoneyRequest request) {
        throw unconfigured();
    }

    @Override
    public TransactionStatus getTransactionStatus(String referenceId, Product product) {
        throw unconfigured();
    }
}
