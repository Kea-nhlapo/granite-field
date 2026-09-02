package za.co.trademesh.modules.document.application;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.document.domain.ConfirmedDocumentField;
import za.co.trademesh.modules.document.domain.DocumentMismatchRule;
import za.co.trademesh.modules.document.domain.DocumentMismatchSeverity;

@Component
class DocumentComparisonRules {

    static final String RULE_SET_VERSION = "document-comparison/v1";
    static final int RULE_VERSION = 1;

    List<ProposedIndicator> evaluate(SourceSnapshot reference, SourceSnapshot compared) {
        Map<FieldKey, String> referenceFields = recognized(reference.fields());
        Map<FieldKey, String> comparedFields = recognized(compared.fields());
        Set<FieldKey> keys = new LinkedHashSet<>(referenceFields.keySet());
        keys.addAll(comparedFields.keySet());

        List<ProposedIndicator> result = new ArrayList<>();
        keys.stream().sorted().forEach(key -> {
            String referenceValue = referenceFields.get(key);
            String comparedValue = comparedFields.get(key);
            if (!equivalent(key.rule(), referenceValue, comparedValue)) {
                result.add(new ProposedIndicator(
                        key.rule(),
                        RULE_VERSION,
                        key.path(),
                        severity(key.rule()),
                        referenceValue,
                        comparedValue,
                        explanation(key.rule())));
            }
        });
        if (reference.sha256().equals(compared.sha256())) {
            result.add(new ProposedIndicator(
                    DocumentMismatchRule.DUPLICATE_DOCUMENT_CONTENT,
                    RULE_VERSION,
                    "document.sha256",
                    DocumentMismatchSeverity.HIGH,
                    reference.sha256(),
                    compared.sha256(),
                    "The two source files have the same content checksum and require duplicate review."));
        }
        result.sort(
                Comparator.comparing((ProposedIndicator value) -> value.rule().name())
                        .thenComparing(ProposedIndicator::fieldPath));
        return List.copyOf(result);
    }

    private static Map<FieldKey, String> recognized(List<ConfirmedDocumentField> fields) {
        Map<FieldKey, String> result = new HashMap<>();
        fields.stream()
                .sorted(Comparator.comparing(ConfirmedDocumentField::path))
                .forEach(field -> classify(field.path()).ifPresent(key -> result.putIfAbsent(key, field.value())));
        return result;
    }

    private static Optional<FieldKey> classify(String path) {
        String normalized = canonicalPath(path);
        if (normalized.equals("quantity") || normalized.endsWith(".quantity") || normalized.endsWith(".qty")) {
            return Optional.of(new FieldKey(
                    DocumentMismatchRule.DOCUMENT_QUANTITY_MISMATCH,
                    normalized.endsWith(".qty")
                            ? normalized.substring(0, normalized.length() - 3) + "quantity"
                            : normalized));
        }
        if (isPricePath(normalized)) {
            return Optional.of(new FieldKey(DocumentMismatchRule.DOCUMENT_PRICE_MISMATCH, pricePath(normalized)));
        }
        if (normalized.equals("supplier") || normalized.startsWith("supplier.")) {
            return Optional.of(new FieldKey(
                    DocumentMismatchRule.DOCUMENT_SUPPLIER_MISMATCH, identityPath(normalized, "supplier")));
        }
        if (normalized.equals("customer")
                || normalized.startsWith("customer.")
                || normalized.equals("buyer")
                || normalized.startsWith("buyer.")) {
            String customer = normalized.startsWith("buyer") ? "customer" + normalized.substring(5) : normalized;
            return Optional.of(
                    new FieldKey(DocumentMismatchRule.DOCUMENT_CUSTOMER_MISMATCH, identityPath(customer, "customer")));
        }
        if (isDestinationPath(normalized)) {
            return Optional.of(new FieldKey(DocumentMismatchRule.DOCUMENT_DESTINATION_MISMATCH, "destination"));
        }
        if (isDatePath(normalized)) {
            return Optional.of(new FieldKey(DocumentMismatchRule.DOCUMENT_DATE_MISMATCH, datePath(normalized)));
        }
        return Optional.empty();
    }

    private static String canonicalPath(String path) {
        String normalized = Normalizer.normalize(path.strip(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replace('[', '.')
                .replace("]", "")
                .replace('_', '.')
                .replace('-', '.')
                .replaceAll("\\s+", "")
                .replaceAll("\\.+", ".");
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized
                .replace("unit.price", "unitprice")
                .replace("suppliername", "supplier.name")
                .replace("customername", "customer.name")
                .replace("buyername", "buyer.name")
                .replace("tax.amount", "taxamount")
                .replace("expected.delivery.date", "expecteddate")
                .replace("expecteddeliverydate", "expecteddate");
    }

    private static boolean isPricePath(String path) {
        return path.equals("price")
                || path.endsWith(".price")
                || path.equals("unitprice")
                || path.endsWith(".unitprice")
                || path.equals("subtotal")
                || path.endsWith(".subtotal")
                || path.equals("taxamount")
                || path.endsWith(".taxamount")
                || path.equals("total")
                || path.endsWith(".total")
                || path.equals("total.value")
                || path.endsWith(".total.value");
    }

    private static String pricePath(String path) {
        if (path.equals("total.value")) {
            return "total";
        }
        return path.endsWith(".total.value") ? path.substring(0, path.length() - ".value".length()) : path;
    }

    private static String identityPath(String path, String identity) {
        return path.equals(identity) ? identity + ".name" : path;
    }

    private static boolean isDestinationPath(String path) {
        return path.equals("destination")
                || path.startsWith("destination.")
                || path.equals("delivery.address")
                || path.equals("shipping.address")
                || path.equals("deliveryaddress")
                || path.equals("shippingaddress");
    }

    private static boolean isDatePath(String path) {
        return path.equals("date")
                || path.endsWith(".date")
                || path.equals("expecteddate")
                || path.endsWith(".expecteddate");
    }

    private static String datePath(String path) {
        return path.equals("expecteddate") || path.endsWith(".expecteddate") ? "delivery.expecteddate" : path;
    }

    private static boolean equivalent(DocumentMismatchRule rule, String left, String right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return switch (rule) {
            case DOCUMENT_QUANTITY_MISMATCH, DOCUMENT_PRICE_MISMATCH ->
                decimal(left)
                        .flatMap(first -> decimal(right).map(second -> first.compareTo(second) == 0))
                        .orElseGet(() -> text(left).equals(text(right)));
            case DOCUMENT_DATE_MISMATCH -> date(left).equals(date(right));
            default -> text(left).equals(text(right));
        };
    }

    private static Optional<BigDecimal> decimal(String raw) {
        String value = Normalizer.normalize(raw, Normalizer.Form.NFKC).strip().replaceAll("[^0-9,\\.\\-+]", "");
        if (value.contains(",") && value.contains(".")) {
            value = value.replace(",", "");
        } else if (value.contains(",")) {
            int commaCount = value.length() - value.replace(",", "").length();
            int fractionLength = value.length() - value.lastIndexOf(',') - 1;
            value = commaCount > 1 || fractionLength == 3 ? value.replace(",", "") : value.replace(',', '.');
        }
        try {
            return Optional.of(new BigDecimal(value));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static String date(String raw) {
        String value = raw.strip();
        try {
            return OffsetDateTime.parse(value).toLocalDate().toString();
        } catch (RuntimeException ignored) {
            // Continue through the less specific ISO formats.
        }
        try {
            return Instant.parse(value)
                    .atOffset(java.time.ZoneOffset.UTC)
                    .toLocalDate()
                    .toString();
        } catch (RuntimeException ignored) {
            // Continue to a plain date or normalized text.
        }
        try {
            return LocalDate.parse(value).toString();
        } catch (RuntimeException ignored) {
            return text(value);
        }
    }

    private static String text(String raw) {
        return Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .strip()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private static DocumentMismatchSeverity severity(DocumentMismatchRule rule) {
        return switch (rule) {
            case DOCUMENT_QUANTITY_MISMATCH,
                    DOCUMENT_PRICE_MISMATCH,
                    DOCUMENT_SUPPLIER_MISMATCH,
                    DOCUMENT_DESTINATION_MISMATCH,
                    DUPLICATE_DOCUMENT_CONTENT -> DocumentMismatchSeverity.HIGH;
            case DOCUMENT_CUSTOMER_MISMATCH, DOCUMENT_DATE_MISMATCH -> DocumentMismatchSeverity.MEDIUM;
        };
    }

    private static String explanation(DocumentMismatchRule rule) {
        return switch (rule) {
            case DOCUMENT_QUANTITY_MISMATCH ->
                "A confirmed quantity differs or is missing between the two source documents.";
            case DOCUMENT_PRICE_MISMATCH ->
                "A confirmed price or amount differs or is missing between the two source documents.";
            case DOCUMENT_SUPPLIER_MISMATCH ->
                "Confirmed supplier information differs or is missing between the two source documents.";
            case DOCUMENT_CUSTOMER_MISMATCH ->
                "Confirmed customer information differs or is missing between the two source documents.";
            case DOCUMENT_DESTINATION_MISMATCH ->
                "The confirmed destination differs or is missing between the two source documents.";
            case DOCUMENT_DATE_MISMATCH -> "A confirmed date differs or is missing between the two source documents.";
            case DUPLICATE_DOCUMENT_CONTENT ->
                "The two source files have the same content checksum and require duplicate review.";
        };
    }

    record SourceSnapshot(String sha256, List<ConfirmedDocumentField> fields) {
        SourceSnapshot {
            fields = List.copyOf(fields);
        }
    }

    record ProposedIndicator(
            DocumentMismatchRule rule,
            int ruleVersion,
            String fieldPath,
            DocumentMismatchSeverity severity,
            String referenceValue,
            String comparedValue,
            String explanation) {}

    private record FieldKey(DocumentMismatchRule rule, String path) implements Comparable<FieldKey> {
        @Override
        public int compareTo(FieldKey other) {
            int ruleOrder = rule.name().compareTo(other.rule.name());
            return ruleOrder == 0 ? path.compareTo(other.path) : ruleOrder;
        }
    }
}
