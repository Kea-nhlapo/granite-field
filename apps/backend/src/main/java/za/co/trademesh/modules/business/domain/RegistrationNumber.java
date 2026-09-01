package za.co.trademesh.modules.business.domain;

import java.util.regex.Pattern;

public record RegistrationNumber(String value) {

    private static final Pattern ALLOWED_INPUT = Pattern.compile("[0-9\\s/-]+");
    private static final Pattern CANONICAL_DIGITS = Pattern.compile("[0-9]{12}");

    public RegistrationNumber {
        if (value == null || !value.matches("[0-9]{4}/[0-9]{6}/[0-9]{2}")) {
            throw new IllegalArgumentException("Registration number is not normalized");
        }
    }

    public static RegistrationNumber from(String rawValue) {
        if (rawValue == null || !ALLOWED_INPUT.matcher(rawValue).matches()) {
            throw new IllegalArgumentException("Registration number contains invalid characters");
        }

        String digits = rawValue.replaceAll("[\\s/-]", "");
        if (!CANONICAL_DIGITS.matcher(digits).matches()) {
            throw new IllegalArgumentException("Registration number must contain 12 digits");
        }

        return new RegistrationNumber(
                digits.substring(0, 4) + "/" + digits.substring(4, 10) + "/" + digits.substring(10));
    }

    public String digits() {
        return value.replace("/", "");
    }

    @Override
    public String toString() {
        return value;
    }
}
