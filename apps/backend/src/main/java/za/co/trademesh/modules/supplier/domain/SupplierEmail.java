package za.co.trademesh.modules.supplier.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public record SupplierEmail(String value) {

    private static final int MAX_LENGTH = 320;
    private static final Pattern SIMPLE_EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public SupplierEmail {
        if (value == null
                || value.length() > MAX_LENGTH
                || !SIMPLE_EMAIL.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid supplier email");
        }
    }

    public static SupplierEmail from(String rawEmail) {
        if (rawEmail == null) {
            throw new IllegalArgumentException("Invalid supplier email");
        }
        return new SupplierEmail(rawEmail.strip().toLowerCase(Locale.ROOT));
    }
}
