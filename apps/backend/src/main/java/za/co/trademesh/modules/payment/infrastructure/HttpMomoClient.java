package za.co.trademesh.modules.payment.infrastructure;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import za.co.trademesh.modules.payment.application.MomoClient;
import za.co.trademesh.modules.payment.application.MomoException;
import za.co.trademesh.modules.payment.application.MomoProperties;

@Component
@ConditionalOnProperty(prefix = "trademesh.integrations.momo", name = "provider", havingValue = "http")
class HttpMomoClient implements MomoClient {

    private static final String SUBSCRIPTION_KEY = "Ocp-Apim-Subscription-Key";
    private static final String TARGET_ENVIRONMENT = "X-Target-Environment";
    private static final String REFERENCE_ID = "X-Reference-Id";
    private static final String CALLBACK_URL = "X-Callback-Url";

    private final RestClient client;
    private final MomoProperties properties;
    private final Clock clock;
    private final Map<Product, AccessToken> tokens = new EnumMap<>(Product.class);

    HttpMomoClient(RestClient.Builder builder, MomoProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        requireLiveConfiguration(properties);
        this.client = builder.baseUrl(properties.baseUrl().toString()).build();
    }

    @Override
    public synchronized AccessToken getToken(Product product) {
        Instant refreshAt = clock.instant().plus(properties.tokenRefreshSkew());
        AccessToken current = tokens.get(product);
        if (current != null && current.expiresAt().isAfter(refreshAt)) {
            return current;
        }

        MomoProperties.ProductCredentials credentials = properties.credentials(product);
        try {
            TokenResponse response = client.post()
                    .uri("/{product}/token/", product.path())
                    .header(HttpHeaders.AUTHORIZATION, basic(credentials.apiUser(), credentials.apiKey()))
                    .header(SUBSCRIPTION_KEY, credentials.subscriptionKey())
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || blank(response.accessToken()) || response.expiresIn() <= 0) {
                throw new MomoException("MOMO_TOKEN_INVALID", "MTN returned an invalid access token", true);
            }
            AccessToken replacement =
                    new AccessToken(response.accessToken(), clock.instant().plusSeconds(response.expiresIn()));
            tokens.put(product, replacement);
            return replacement;
        } catch (RestClientResponseException failure) {
            throw responseFailure("MOMO_TOKEN", failure);
        } catch (ResourceAccessException failure) {
            throw unavailable("MOMO_TOKEN_UNAVAILABLE");
        }
    }

    @Override
    public ConsentRequest bcAuthorize(String phoneNumber) {
        String reference = UUID.randomUUID().toString();
        Product product = Product.COLLECTIONS;
        try {
            client.post()
                    .uri("/{product}/v1_0/bc-authorize", product.path())
                    .headers(headers -> authorize(headers, product))
                    .header(REFERENCE_ID, reference)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ConsentBody("MSISDN:" + phoneNumber, "openid profile"))
                    .retrieve()
                    .toBodilessEntity();
            return new ConsentRequest(reference, ConsentStatus.PENDING);
        } catch (RestClientResponseException failure) {
            throw responseFailure("MOMO_CONSENT", failure);
        } catch (ResourceAccessException failure) {
            throw unavailable("MOMO_CONSENT_UNAVAILABLE");
        }
    }

    @Override
    public ConsentStatus getConsentStatus(String providerReference) {
        Product product = Product.COLLECTIONS;
        try {
            StatusResponse response = client.get()
                    .uri("/{product}/v1_0/bc-authorize/{reference}", product.path(), providerReference)
                    .headers(headers -> authorize(headers, product))
                    .retrieve()
                    .body(StatusResponse.class);
            return response == null ? ConsentStatus.PENDING : parseConsentStatus(response.status());
        } catch (RestClientResponseException failure) {
            if (failure.getStatusCode().value() == 404) {
                return ConsentStatus.EXPIRED;
            }
            throw responseFailure("MOMO_CONSENT_STATUS", failure);
        } catch (ResourceAccessException failure) {
            throw unavailable("MOMO_CONSENT_STATUS_UNAVAILABLE");
        }
    }

    @Override
    public UserInfo getBasicUserInfo(String providerReference) {
        Product product = Product.COLLECTIONS;
        try {
            UserInfoResponse response = client.post()
                    .uri("/{product}/oauth2/v1_0/userinfo", product.path())
                    .headers(headers -> authorize(headers, product))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new UserInfoBody(providerReference))
                    .retrieve()
                    .body(UserInfoResponse.class);
            if (response == null) {
                throw new MomoException("MOMO_USERINFO_INVALID", "MTN returned no user information", true);
            }
            return new UserInfo(response.givenName(), response.familyName(), response.locale());
        } catch (RestClientResponseException failure) {
            throw responseFailure("MOMO_USERINFO", failure);
        } catch (ResourceAccessException failure) {
            throw unavailable("MOMO_USERINFO_UNAVAILABLE");
        }
    }

    @Override
    public boolean validateAccountHolder(String phoneNumber) {
        Product product = Product.DISBURSEMENTS;
        try {
            AccountHolderResponse response = client.get()
                    .uri("/{product}/v1_0/accountholder/msisdn/{phone}/active", product.path(), phoneNumber)
                    .headers(headers -> authorize(headers, product))
                    .retrieve()
                    .body(AccountHolderResponse.class);
            return response != null && response.result();
        } catch (RestClientResponseException failure) {
            throw responseFailure("MOMO_ACCOUNT_HOLDER", failure);
        } catch (ResourceAccessException failure) {
            throw unavailable("MOMO_ACCOUNT_HOLDER_UNAVAILABLE");
        }
    }

    @Override
    public Balance getBalance(Product product) {
        try {
            BalanceResponse response = client.get()
                    .uri("/{product}/v1_0/account/balance", product.path())
                    .headers(headers -> authorize(headers, product))
                    .retrieve()
                    .body(BalanceResponse.class);
            if (response == null || blank(response.availableBalance())) {
                throw new MomoException("MOMO_BALANCE_INVALID", "MTN returned no balance", true);
            }
            return new Balance(new java.math.BigDecimal(response.availableBalance()), response.currency());
        } catch (RestClientResponseException failure) {
            throw responseFailure("MOMO_BALANCE", failure);
        } catch (ResourceAccessException failure) {
            throw unavailable("MOMO_BALANCE_UNAVAILABLE");
        }
    }

    @Override
    public String requestToPay(MoneyRequest request) {
        submitMoneyRequest(request, Product.COLLECTIONS, "requesttopay", "payer");
        return request.referenceId();
    }

    @Override
    public String transfer(MoneyRequest request) {
        submitMoneyRequest(request, Product.DISBURSEMENTS, "transfer", "payee");
        return request.referenceId();
    }

    @Override
    public TransactionStatus getTransactionStatus(String referenceId, Product product) {
        String operation = product == Product.COLLECTIONS ? "requesttopay" : "transfer";
        try {
            StatusResponse response = client.get()
                    .uri("/{product}/v1_0/{operation}/{reference}", product.path(), operation, referenceId)
                    .headers(headers -> authorize(headers, product))
                    .retrieve()
                    .body(StatusResponse.class);
            return response == null ? TransactionStatus.UNKNOWN : parseTransactionStatus(response.status());
        } catch (RestClientResponseException failure) {
            if (failure.getStatusCode().value() == 404) {
                return TransactionStatus.UNKNOWN;
            }
            throw responseFailure("MOMO_TRANSACTION_STATUS", failure);
        } catch (ResourceAccessException failure) {
            throw unavailable("MOMO_TRANSACTION_STATUS_UNAVAILABLE");
        }
    }

    private void submitMoneyRequest(MoneyRequest request, Product product, String operation, String partyField) {
        if (request.amount() == null || request.amount().signum() <= 0 || blank(request.referenceId())) {
            throw new MomoException("MOMO_REQUEST_INVALID", "Mobile Money request is invalid", false);
        }
        Party party = new Party("MSISDN", request.phoneNumber());
        MoneyBody body = new MoneyBody(
                request.amount().toPlainString(),
                properties.currency(),
                request.referenceId(),
                "payer".equals(partyField) ? party : null,
                "payee".equals(partyField) ? party : null,
                "TradeMesh transaction",
                "TradeMesh transaction");
        try {
            RestClient.RequestBodySpec requestSpec = client.post()
                    .uri("/{product}/v1_0/{operation}", product.path(), operation)
                    .headers(headers -> authorize(headers, product))
                    .header(REFERENCE_ID, request.referenceId())
                    .contentType(MediaType.APPLICATION_JSON);
            if (properties.callbackHost() != null) {
                requestSpec.header(
                        CALLBACK_URL,
                        properties
                                .callbackHost()
                                .resolve("/api/payments/momo/callback")
                                .toString());
            }
            requestSpec.body(body).retrieve().toBodilessEntity();
        } catch (RestClientResponseException failure) {
            throw responseFailure("MOMO_TRANSACTION", failure);
        } catch (ResourceAccessException failure) {
            throw unavailable("MOMO_TRANSACTION_UNAVAILABLE");
        }
    }

    private void authorize(HttpHeaders headers, Product product) {
        headers.setBearerAuth(getToken(product).value());
        headers.set(SUBSCRIPTION_KEY, properties.credentials(product).subscriptionKey());
        headers.set(TARGET_ENVIRONMENT, properties.targetEnvironment());
    }

    private static String basic(String username, String password) {
        String raw = username + ":" + password;
        return "Basic " + java.util.Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static ConsentStatus parseConsentStatus(String value) {
        if (blank(value)) {
            return ConsentStatus.PENDING;
        }
        try {
            return ConsentStatus.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return ConsentStatus.PENDING;
        }
    }

    private static TransactionStatus parseTransactionStatus(String value) {
        if (blank(value)) {
            return TransactionStatus.UNKNOWN;
        }
        try {
            return TransactionStatus.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return TransactionStatus.UNKNOWN;
        }
    }

    private static MomoException responseFailure(String prefix, RestClientResponseException failure) {
        int status = failure.getStatusCode().value();
        return new MomoException(
                prefix + "_HTTP_" + status, "MTN returned HTTP " + status, status == 429 || status >= 500);
    }

    private static MomoException unavailable(String code) {
        return new MomoException(code, "MTN could not be reached", true);
    }

    private static void requireLiveConfiguration(MomoProperties properties) {
        if (!properties.collections().configured()
                || !properties.disbursements().configured()) {
            throw new IllegalStateException("Both MTN MoMo products require subscription and API credentials");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record TokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token")
            String accessToken,

            @com.fasterxml.jackson.annotation.JsonProperty("expires_in")
            long expiresIn) {}

    private record ConsentBody(
            @com.fasterxml.jackson.annotation.JsonProperty("login_hint")
            String loginHint,

            String scope) {}

    private record UserInfoBody(String referenceId) {}

    private record UserInfoResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("given_name")
            String givenName,

            @com.fasterxml.jackson.annotation.JsonProperty("family_name")
            String familyName,

            String locale) {}

    private record AccountHolderResponse(boolean result) {}

    private record StatusResponse(String status) {}

    private record BalanceResponse(String availableBalance, String currency) {}

    private record Party(String partyIdType, String partyId) {}

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private record MoneyBody(
            String amount,
            String currency,
            String externalId,
            Party payer,
            Party payee,
            String payerMessage,
            String payeeNote) {}
}
