package za.co.trademesh.modules.notification.application;

import java.util.regex.Pattern;

final class PhoneNumbers {

    private static final Pattern E164 = Pattern.compile("^[+][1-9][0-9]{7,14}$");

    private PhoneNumbers() {}

    static String normalize(String value) {
        if (value == null) {
            throw NotificationException.invalidPhone();
        }
        String normalized = value.replace(" ", "").replace("-", "").strip();
        if (!E164.matcher(normalized).matches()) {
            throw NotificationException.invalidPhone();
        }
        return normalized;
    }

    static String lastFour(String normalized) {
        return normalized.substring(normalized.length() - 4);
    }

    static String mask(String lastFour) {
        return "********" + lastFour;
    }
}
