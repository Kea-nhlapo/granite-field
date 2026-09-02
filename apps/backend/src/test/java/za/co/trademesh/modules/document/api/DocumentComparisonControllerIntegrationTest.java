package za.co.trademesh.modules.document.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import za.co.trademesh.modules.access.application.AuthService;
import za.co.trademesh.modules.access.domain.RegistrationType;
import za.co.trademesh.modules.business.application.RegisteredBusinessOnboardingService;
import za.co.trademesh.modules.document.domain.DocumentType;
import za.co.trademesh.modules.document.events.DocumentEvent;
import za.co.trademesh.shared.events.PublishedEvent;
import za.co.trademesh.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
@RecordApplicationEvents
class DocumentComparisonControllerIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private RegisteredBusinessOnboardingService onboardingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEvents applicationEvents;

    @BeforeEach
    @AfterEach
    void cleanState() {
        jdbcTemplate.update("DELETE FROM document_mismatch_indicator");
        jdbcTemplate.update("DELETE FROM document_comparison");
        jdbcTemplate.update("DELETE FROM procurement_order_item");
        jdbcTemplate.update("DELETE FROM procurement_order");
        jdbcTemplate.update("DELETE FROM procurement_quote_item");
        jdbcTemplate.update("DELETE FROM procurement_quote");
        jdbcTemplate.update("DELETE FROM procurement_request_item");
        jdbcTemplate.update("DELETE FROM procurement_request");
        jdbcTemplate.update("DELETE FROM document_confirmed_field");
        jdbcTemplate.update("DELETE FROM document_confirmation");
        jdbcTemplate.update("DELETE FROM document_extracted_field");
        jdbcTemplate.update("DELETE FROM document_extraction");
        jdbcTemplate.update("DELETE FROM document_state_transition");
        jdbcTemplate.update("DELETE FROM document_record");
        jdbcTemplate.update("DELETE FROM outbox_message");
        jdbcTemplate.update("DELETE FROM stored_file");
        jdbcTemplate.update("DELETE FROM supplier_invitation");
        jdbcTemplate.update("DELETE FROM supplier_profile");
        jdbcTemplate.update("DELETE FROM access_refresh_session");
        jdbcTemplate.update("DELETE FROM access_business_membership");
        jdbcTemplate.update("DELETE FROM business_registered_onboarding");
        jdbcTemplate.update("DELETE FROM business_profile");
        jdbcTemplate.update("DELETE FROM access_user_role");
        jdbcTemplate.update("DELETE FROM access_user_account");
    }

    @Test
    void recordsReviewableMismatchesIdempotentlyAndKeepsEarlierRevisionEvidence() throws Exception {
        Account owner = register("owner@example.com");
        Account outsider = register("outsider@example.com");
        UUID businessId = createBusiness(owner, "2026/710001/07");
        UUID outsiderBusinessId = createBusiness(outsider, "2026/710002/07");
        Map<String, String> requestFields =
                commercialFields("100", "10.00", "ABC", "Kea Store", "Tembisa", "2026-09-04");
        Map<String, String> invoiceFields =
                commercialFields("130", "11.00", "XYZ", "Other Store", "Alexandra", "2026-09-05");
        UUID requestDocument = createConfirmedDocument(
                businessId, owner.userId(), DocumentType.PURCHASE_ORDER, "a".repeat(64), requestFields);
        UUID invoiceDocument = createConfirmedDocument(
                businessId, owner.userId(), DocumentType.INVOICE, "b".repeat(64), invoiceFields);
        UUID commandId = UUID.randomUUID();

        String first = compare(owner, businessId, commandId, requestDocument, invoiceDocument)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleSetVersion").value("document-comparison/v1"))
                .andExpect(jsonPath("$.reference.confirmationRevision").value(1))
                .andExpect(jsonPath("$.compared.confirmationRevision").value(1))
                .andExpect(jsonPath("$.mismatches.length()").value(6))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID firstComparison = UUID.fromString(JsonPath.read(first, "$.comparisonId"));
        assertThat(rules(first))
                .containsExactlyInAnyOrder(
                        "DOCUMENT_QUANTITY_MISMATCH",
                        "DOCUMENT_PRICE_MISMATCH",
                        "DOCUMENT_SUPPLIER_MISMATCH",
                        "DOCUMENT_CUSTOMER_MISMATCH",
                        "DOCUMENT_DESTINATION_MISMATCH",
                        "DOCUMENT_DATE_MISMATCH");
        assertThat(first.toLowerCase()).doesNotContain("fraud", "theft", "guilty");
        assertThat(field(first, "DOCUMENT_QUANTITY_MISMATCH", "reference.confirmedValue"))
                .isEqualTo("100");
        assertThat(field(first, "DOCUMENT_QUANTITY_MISMATCH", "compared.confirmedValue"))
                .isEqualTo("130");

        compare(owner, businessId, commandId, requestDocument, invoiceDocument)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comparisonId").value(firstComparison.toString()));
        compare(owner, businessId, UUID.randomUUID(), requestDocument, invoiceDocument)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comparisonId").value(firstComparison.toString()));
        assertThat(rowCount("document_comparison")).isOne();
        assertThat(rowCount("document_mismatch_indicator")).isEqualTo(6);
        assertThat(comparisonCompletedEventCount()).isOne();

        compare(outsider, businessId, UUID.randomUUID(), requestDocument, invoiceDocument)
                .andExpect(status().isForbidden());
        getComparison(outsider, outsiderBusinessId, firstComparison)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_COMPARISON_NOT_FOUND"));

        addCorrection(invoiceDocument, owner.userId(), requestFields);
        String corrected = compare(owner, businessId, UUID.randomUUID(), requestDocument, invoiceDocument)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.compared.confirmationRevision").value(2))
                .andExpect(jsonPath("$.mismatches.length()").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(JsonPath.<String>read(corrected, "$.comparisonId")).isNotEqualTo(firstComparison.toString());

        getComparison(owner, businessId, firstComparison)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compared.confirmationRevision").value(1))
                .andExpect(jsonPath("$.mismatches.length()").value(6));
        assertThat(rowCount("document_comparison")).isEqualTo(2);
        assertThat(rowCount("document_mismatch_indicator")).isEqualTo(6);
    }

    @Test
    void flagsDuplicateContentAndRejectsUnsupportedOrUnconfirmedSources() throws Exception {
        Account owner = register("owner@example.com");
        UUID businessId = createBusiness(owner, "2026/720001/07");
        Map<String, String> fields = commercialFields("20", "8.50", "ABC", "Kea", "Tembisa", "2026-09-04");
        String checksum = "c".repeat(64);
        UUID quote = createConfirmedDocument(businessId, owner.userId(), DocumentType.QUOTE, checksum, fields);
        UUID invoice = createConfirmedDocument(businessId, owner.userId(), DocumentType.INVOICE, checksum, fields);

        String duplicate = compare(owner, businessId, UUID.randomUUID(), quote, invoice)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mismatches.length()").value(1))
                .andExpect(jsonPath("$.mismatches[0].rule").value("DUPLICATE_DOCUMENT_CONTENT"))
                .andExpect(jsonPath("$.mismatches[0].ruleVersion").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(duplicate.toLowerCase()).contains("duplicate review").doesNotContain("fraud");

        UUID companyDocument = createConfirmedDocument(
                businessId, owner.userId(), DocumentType.COMPANY_DOCUMENT, "d".repeat(64), fields);
        compare(owner, businessId, UUID.randomUUID(), quote, companyDocument)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DOCUMENT_COMPARISON_SOURCE_UNSUPPORTED"));

        UUID unconfirmed = createUnconfirmedDocument(businessId, owner.userId(), DocumentType.DELIVERY_NOTE);
        compare(owner, businessId, UUID.randomUUID(), quote, unconfirmed)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_COMPARISON_SOURCE_UNAVAILABLE"));
        compare(owner, businessId, UUID.randomUUID(), quote, quote)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT_COMPARISON"));
    }

    private Account register(String email) {
        var tokens = authService.register(email, "correct-horse-battery", RegistrationType.BUSINESS_OWNER);
        return new Account(tokens.userId(), tokens.accessToken());
    }

    private UUID createBusiness(Account owner, String registrationNumber) {
        var onboarding = onboardingService.start(registrationNumber, owner.userId());
        return onboardingService.confirm(onboarding.id(), owner.userId()).id();
    }

    private UUID createConfirmedDocument(
            UUID businessId, UUID actorUserId, DocumentType type, String checksum, Map<String, String> fields) {
        UUID documentId = createDocument(businessId, actorUserId, type, checksum, "CONFIRMED");
        addConfirmation(documentId, actorUserId, 1, fields);
        return documentId;
    }

    private UUID createUnconfirmedDocument(UUID businessId, UUID actorUserId, DocumentType type) {
        return createDocument(businessId, actorUserId, type, "e".repeat(64), "PARSED");
    }

    private UUID createDocument(UUID businessId, UUID actorUserId, DocumentType type, String checksum, String state) {
        UUID storedFileId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO stored_file (
                id, business_id, category, original_filename, object_key, content_type,
                extension, size_bytes, sha256, scan_status, storage_status,
                uploaded_by_user_id, created_at, stored_at
            ) VALUES (?, ?, 'INVOICE', 'source.pdf', ?, 'application/pdf',
                      'pdf', 10, ?, 'CLEAN', 'AVAILABLE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, storedFileId, businessId, "comparison-test/" + storedFileId, checksum, actorUserId);
        jdbcTemplate.update(
                """
            INSERT INTO document_record (
                id, business_id, stored_file_id, client_request_id, document_type, state,
                processing_attempts, created_by_user_id, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, 1, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, documentId, businessId, storedFileId, UUID.randomUUID(), type.name(), state, actorUserId);
        return documentId;
    }

    private void addCorrection(UUID documentId, UUID actorUserId, Map<String, String> fields) {
        addConfirmation(documentId, actorUserId, 2, fields);
    }

    private void addConfirmation(UUID documentId, UUID actorUserId, int revision, Map<String, String> fields) {
        UUID confirmationId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO document_confirmation (
                id, document_id, request_id, revision, confirmed_by_user_id, created_at
            ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """, confirmationId, documentId, UUID.randomUUID(), revision, actorUserId);
        fields.forEach((path, value) -> jdbcTemplate.update(
                "INSERT INTO document_confirmed_field (confirmation_id, field_path, field_value) VALUES (?, ?, ?)",
                confirmationId,
                path,
                value));
    }

    private ResultActions compare(
            Account account, UUID businessId, UUID requestId, UUID referenceDocumentId, UUID comparedDocumentId)
            throws Exception {
        String body = """
            {
              "requestId":"%s",
              "referenceDocumentId":"%s",
              "comparedDocumentId":"%s"
            }
            """.formatted(requestId, referenceDocumentId, comparedDocumentId);
        return mockMvc.perform(post("/api/businesses/{businessId}/documents/comparisons", businessId)
                .header(HttpHeaders.AUTHORIZATION, bearer(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions getComparison(Account account, UUID businessId, UUID comparisonId) throws Exception {
        return mockMvc.perform(
                get("/api/businesses/{businessId}/documents/comparisons/{comparisonId}", businessId, comparisonId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(account)));
    }

    @SuppressWarnings("unchecked")
    private static List<String> rules(String json) {
        List<Map<String, Object>> mismatches = JsonPath.read(json, "$.mismatches");
        return mismatches.stream().map(value -> value.get("rule").toString()).toList();
    }

    @SuppressWarnings("unchecked")
    private static String field(String json, String rule, String nestedPath) {
        List<Map<String, Object>> mismatches = JsonPath.read(json, "$.mismatches");
        Map<String, Object> mismatch = mismatches.stream()
                .filter(value -> rule.equals(value.get("rule")))
                .findFirst()
                .orElseThrow();
        Object value = mismatch;
        for (String part : nestedPath.split("\\.")) {
            value = ((Map<String, Object>) value).get(part);
        }
        return value == null ? null : value.toString();
    }

    private int rowCount(String table) {
        if (!table.equals("document_comparison") && !table.equals("document_mismatch_indicator")) {
            throw new IllegalArgumentException("Unexpected table");
        }
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private long comparisonCompletedEventCount() {
        return applicationEvents.stream(PublishedEvent.class)
                .map(event -> ((PublishedEvent<?>) event).event())
                .filter(DocumentEvent.ComparisonCompleted.class::isInstance)
                .count();
    }

    private static Map<String, String> commercialFields(
            String quantity, String price, String supplier, String customer, String destination, String date) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("items.coke.quantity", quantity);
        fields.put("items.coke.unitPrice", price);
        fields.put("supplier.name", supplier);
        fields.put("customer.name", customer);
        fields.put("destination.address", destination);
        fields.put("delivery.expectedDate", date);
        return fields;
    }

    private static String bearer(Account account) {
        return "Bearer " + account.accessToken();
    }

    private record Account(UUID userId, String accessToken) {}
}
