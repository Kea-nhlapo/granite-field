package za.co.trademesh.modules.routing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Carries its currency because this product routes across SADC borders, where a
 * bare amount is ambiguous. BigDecimal rather than double: tolls are money.
 *
 * <p>The amount is normalised to the currency's own fraction digits on the way
 * in. Records derive equals from their components, and BigDecimal.equals is
 * SCALE-sensitive — without this, 1.50 and 1.5 would be unequal Money values
 * representing the same rand, and every comparison or dedup downstream would be
 * quietly wrong in a way that looks like a data fault.
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(currency, "currency is required");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative, was " + amount);
        }
        int fractionDigits = currency.getDefaultFractionDigits();
        if (fractionDigits >= 0) {
            // Pseudo-currencies such as XXX report -1 and are left as supplied.
            amount = amount.setScale(fractionDigits, RoundingMode.HALF_UP);
        }
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode));
    }
}
