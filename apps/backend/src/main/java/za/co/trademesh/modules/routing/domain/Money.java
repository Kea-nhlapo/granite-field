package za.co.trademesh.modules.routing.domain;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * Carries its currency because this product routes across SADC borders, where a
 * bare amount is ambiguous. BigDecimal rather than double: tolls are money.
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(currency, "currency is required");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative, was " + amount);
        }
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode));
    }
}
