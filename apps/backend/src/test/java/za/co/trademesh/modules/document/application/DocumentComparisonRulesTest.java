package za.co.trademesh.modules.document.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import za.co.trademesh.modules.document.domain.ConfirmedDocumentField;
import za.co.trademesh.modules.document.domain.DocumentMismatchRule;

class DocumentComparisonRulesTest {

    private final DocumentComparisonRules rules = new DocumentComparisonRules();

    @Test
    void coversEveryRuleWithNeutralVersionedIndicators() {
        var reference = source(
                "a".repeat(64),
                field("items.coke.quantity", "100"),
                field("items.coke.unit_price", "R 10.00"),
                field("supplierName", "ABC Wholesalers"),
                field("buyer.name", "Kea Store"),
                field("delivery.address", "Tembisa"),
                field("expectedDeliveryDate", "2026-09-04"));
        var compared = source(
                "a".repeat(64),
                field("items.coke.qty", "130"),
                field("items.coke.unitPrice", "11.00"),
                field("supplier.name", "XYZ Wholesalers"),
                field("customer.name", "Another Store"),
                field("destination.address", "Alexandra"),
                field("delivery.expectedDate", "2026-09-05T08:00:00Z"));

        var indicators = rules.evaluate(reference, compared);

        assertThat(indicators)
                .extracting(DocumentComparisonRules.ProposedIndicator::rule)
                .containsExactlyInAnyOrder(
                        DocumentMismatchRule.DOCUMENT_QUANTITY_MISMATCH,
                        DocumentMismatchRule.DOCUMENT_PRICE_MISMATCH,
                        DocumentMismatchRule.DOCUMENT_SUPPLIER_MISMATCH,
                        DocumentMismatchRule.DOCUMENT_CUSTOMER_MISMATCH,
                        DocumentMismatchRule.DOCUMENT_DESTINATION_MISMATCH,
                        DocumentMismatchRule.DOCUMENT_DATE_MISMATCH,
                        DocumentMismatchRule.DUPLICATE_DOCUMENT_CONTENT);
        assertThat(indicators).allSatisfy(indicator -> {
            assertThat(indicator.ruleVersion()).isEqualTo(1);
            assertThat(indicator.explanation().toLowerCase()).doesNotContain("fraud", "theft", "guilty");
        });
    }

    @Test
    void ignoresFormattingDifferencesButFlagsAValueMissingFromOneSource() {
        var reference = source(
                "a".repeat(64),
                field("items.oil.quantity", "1,000"),
                field("total.value", "ZAR 8,500.00"),
                field("supplier.name", "  ABC   Wholesalers "),
                field("delivery.expectedDate", "2026-09-04T08:00:00Z"),
                field("customer.name", "Kea Store"));
        var compared = source(
                "b".repeat(64),
                field("items.oil.quantity", "1000"),
                field("total", "8500"),
                field("supplier.name", "abc wholesalers"),
                field("delivery.expectedDate", "2026-09-04"));

        var indicators = rules.evaluate(reference, compared);

        assertThat(indicators).singleElement().satisfies(indicator -> {
            assertThat(indicator.rule()).isEqualTo(DocumentMismatchRule.DOCUMENT_CUSTOMER_MISMATCH);
            assertThat(indicator.referenceValue()).isEqualTo("Kea Store");
            assertThat(indicator.comparedValue()).isNull();
        });
    }

    private static DocumentComparisonRules.SourceSnapshot source(String sha256, ConfirmedDocumentField... fields) {
        return new DocumentComparisonRules.SourceSnapshot(sha256, List.of(fields));
    }

    private static ConfirmedDocumentField field(String path, String value) {
        return new ConfirmedDocumentField(path, value);
    }
}
