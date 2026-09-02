package za.co.trademesh.modules.payment.application;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Public payment-module boundary. Business modules never depend on MTN HTTP
 * details or credentials.
 */
public interface MomoClient {

    AccessToken getToken(Product product);

    ConsentRequest bcAuthorize(String phoneNumber);

    ConsentStatus getConsentStatus(String providerReference);

    UserInfo getBasicUserInfo(String providerReference);

    boolean validateAccountHolder(String phoneNumber);

    String requestToPay(MoneyRequest request);

    String transfer(MoneyRequest request);

    TransactionStatus getTransactionStatus(String referenceId, Product product);

    enum Product {
        COLLECTIONS("collection"),
        DISBURSEMENTS("disbursement");

        private final String path;

        Product(String path) {
            this.path = path;
        }

        public String path() {
            return path;
        }
    }

    enum ConsentStatus {
        PENDING,
        APPROVED,
        REJECTED,
        EXPIRED
    }

    enum TransactionStatus {
        PENDING,
        SUCCESSFUL,
        FAILED,
        UNKNOWN
    }

    record AccessToken(String value, Instant expiresAt) {}

    record ConsentRequest(String providerReference, ConsentStatus status) {}

    record UserInfo(String givenName, String familyName, String locale) {}

    record MoneyRequest(String phoneNumber, BigDecimal amount, String referenceId) {}
}
