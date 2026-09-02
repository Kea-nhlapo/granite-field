package za.co.trademesh.modules.access.application;

import java.util.regex.Pattern;

final class PhoneNumbers {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");

    private PhoneNumbers() {}

    static String normalize(String value) {
        if (value == null) {
            throw AccessException.invalidPhoneNumber();
        }
        String normalized = value.replace(" ", "").replace("-", "").strip();
        if (!E164.matcher(normalized).matches()) {
            throw AccessException.invalidPhoneNumber();
        }
        return normalized;
    }
}
