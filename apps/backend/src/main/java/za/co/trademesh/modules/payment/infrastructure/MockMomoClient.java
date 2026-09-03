package za.co.trademesh.modules.payment.infrastructure;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.payment.application.MomoClient;
import za.co.trademesh.modules.payment.application.MomoException;

@Component
@ConditionalOnProperty(prefix = "trademesh.integrations.momo", name = "provider", havingValue = "mock")
class MockMomoClient implements MomoClient {

    private final Clock clock;
    private final Map<String, String> consentPhones = new ConcurrentHashMap<>();
    private final Map<String, TransactionStatus> transactions = new ConcurrentHashMap<>();

    MockMomoClient(Clock clock) {
        this.clock = clock;
    }

    @Override
    public AccessToken getToken(Product product) {
        return new AccessToken(
                "mock-" + product.name().toLowerCase(), clock.instant().plus(Duration.ofHours(1)));
    }

    @Override
    public ConsentRequest bcAuthorize(String phoneNumber) {
        String reference = UUID.randomUUID().toString();
        consentPhones.put(reference, phoneNumber);
        return new ConsentRequest(reference, ConsentStatus.APPROVED);
    }

    @Override
    public ConsentStatus getConsentStatus(String providerReference) {
        return consentPhones.containsKey(providerReference) ? ConsentStatus.APPROVED : ConsentStatus.EXPIRED;
    }

    @Override
    public UserInfo getBasicUserInfo(String providerReference) {
        if (!consentPhones.containsKey(providerReference)) {
            throw new MomoException("MOMO_CONSENT_UNKNOWN", "Mobile Money consent was not found", false);
        }
        return new UserInfo("Demo", "Business Owner", "en-ZA");
    }

    @Override
    public boolean validateAccountHolder(String phoneNumber) {
        return !phoneNumber.endsWith("0000");
    }

    @Override
    public Balance getBalance(Product product) {
        BigDecimal amount = product == Product.COLLECTIONS ? new BigDecimal("15420.75") : new BigDecimal("8930.00");
        return new Balance(amount, "EUR");
    }

    @Override
    public String requestToPay(MoneyRequest request) {
        requirePositive(request.amount());
        transactions.put(request.referenceId(), TransactionStatus.SUCCESSFUL);
        return request.referenceId();
    }

    @Override
    public String transfer(MoneyRequest request) {
        requirePositive(request.amount());
        transactions.put(request.referenceId(), TransactionStatus.SUCCESSFUL);
        return request.referenceId();
    }

    @Override
    public TransactionStatus getTransactionStatus(String referenceId, Product product) {
        return transactions.getOrDefault(referenceId, TransactionStatus.UNKNOWN);
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new MomoException("MOMO_AMOUNT_INVALID", "Mobile Money amount must be positive", false);
        }
    }
}
