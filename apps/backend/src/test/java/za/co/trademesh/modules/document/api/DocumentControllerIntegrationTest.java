package za.co.trademesh.modules.document.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import za.co.trademesh.modules.access.application.AuthService;
import za.co.trademesh.modules.access.domain.RegistrationType;
import za.co.trademesh.modules.business.application.RegisteredBusinessOnboardingService;
import za.co.trademesh.modules.document.application.DocumentExtractionProvider;
import za.co.trademesh.modules.document.application.DocumentExtractionRequested;
import za.co.trademesh.modules.document.domain.ExtractedDocumentField;
import za.co.trademesh.modules.document.events.DocumentEvent;
import za.co.trademesh.shared.events.PublishedEvent;
import za.co.trademesh.shared.events.outbox.OutboxWorker;
import za.co.trademesh.shared.storage.FileCategory;
import za.co.trademesh.shared.storage.FileStorageService;
import za.co.trademesh.shared.storage.StoredFile;
import za.co.trademesh.shared.storage.support.InMemoryObjectStorage;
import za.co.trademesh.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
@RecordApplicationEvents
@Import(DocumentControllerIntegrationTest.DocumentTestConfiguration.class)
class DocumentControllerIntegrationTest extends PostgresIntegrationTest {

    private static final byte[] PDF = "%PDF-1.7\nimmutable invoice".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private RegisteredBusinessOnboardingService onboardingService;

    @Autowired
    private FileStorageService storage;

    @Autowired
    private InMemoryObjectStorage objectStorage;

    @Autowired
    private MutableExtractionProvider extractionProvider;

    @Autowired
    private OutboxWorker outboxWorker;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEvents applicationEvents;

    @BeforeEach
    @AfterEach
    void cleanState() {
        objectStorage.clear();
        extractionProvider.reset();
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
    void processesAnImmutableSourceAndKeepsCorrectionsSeparateFromExtraction() throws Exception {
        Account owner = register("owner@example.com");
        UUID businessId = createBusiness(owner, "2026/111111/07");
        StoredFile source = uploadSource(owner, businessId, "invoice.pdf");
        UUID registrationRequest = UUID.randomUUID();

        String queued = registerDocument(owner, businessId, source.id(), registrationRequest)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.state").value("QUEUED"))
                .andExpect(jsonPath("$.extraction").doesNotExist())
                .andExpect(jsonPath("$.stateHistory[0].toState").value("UPLOADED"))
                .andExpect(jsonPath("$.stateHistory[1].toState").value("QUEUED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID documentId = UUID.fromString(JsonPath.read(queued, "$.documentId"));

        assertThat(outboxWorker.pollOnce()).isEqualTo(1);
        String parsed = getDocument(owner, businessId, documentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PARSED"))
                .andExpect(jsonPath("$.extraction.provider").value("test-document-extractor"))
                .andExpect(jsonPath("$.extraction.parserVersion").value("test-1.0"))
                .andExpect(jsonPath("$.extraction.rawResultReference").value("test-result:" + documentId))
                .andExpect(jsonPath("$.extraction.fields[0].confidence").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(fieldValue(parsed, "$.extraction.fields", "supplier.name")).isEqualTo("Demo Supplier");
        assertThat(objectStorage.content(source.objectKey())).containsExactly(PDF);

        // Replay the already completed delivery. The handler must do nothing,
        // not create another extraction or call the provider again.
        jdbcTemplate.update("""
            UPDATE outbox_message
               SET status = 'PENDING', attempts = 0,
                   available_at = CURRENT_TIMESTAMP - INTERVAL '1 minute', updated_at = now()
             WHERE type = ?
            """, DocumentExtractionRequested.TYPE);
        assertThat(outboxWorker.pollOnce()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM document_extraction", Integer.class))
                .isEqualTo(1);
        assertThat(extractionProvider.calls()).isEqualTo(1);

        UUID confirmationRequest = UUID.randomUUID();
        String confirmationBody = confirmationBody(confirmationRequest, "Corrected Supplier", "8500.00");
        String confirmed = confirm(owner, businessId, documentId, confirmationBody)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmation.revision").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(fieldValue(confirmed, "$.extraction.fields", "supplier.name"))
                .isEqualTo("Demo Supplier");
        assertThat(fieldValue(confirmed, "$.confirmation.fields", "supplier.name"))
                .isEqualTo("Corrected Supplier");

        // The request id makes a browser retry safe.
        confirm(owner, businessId, documentId, confirmationBody)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmation.revision").value(1));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM document_confirmation", Integer.class))
                .isEqualTo(1);
        assertThat(confirmedEventCount()).isEqualTo(1);

        // A later correction is a new revision, while extraction revision zero
        // remains unchanged as evidence of what the parser originally saw.
        confirm(owner, businessId, documentId, confirmationBody(UUID.randomUUID(), "Final Supplier", "8500.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmation.revision").value(2));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM document_extraction", Integer.class))
                .isEqualTo(1);
        assertThat(confirmedEventCount()).isEqualTo(2);
    }

    @Test
    void registrationAndReadsAreIdempotentAndTenantScoped() throws Exception {
        Account owner = register("owner@example.com");
        Account outsider = register("outsider@example.com");
        UUID ownerBusiness = createBusiness(owner, "2026/222222/07");
        UUID outsiderBusiness = createBusiness(outsider, "2026/333333/07");
        StoredFile first = uploadSource(owner, ownerBusiness, "first.pdf");
        StoredFile second = uploadSource(owner, ownerBusiness, "second.pdf");
        UUID requestId = UUID.randomUUID();

        registerDocument(owner, ownerBusiness, UUID.randomUUID(), UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_SOURCE_NOT_FOUND"));

        String created = registerDocument(owner, ownerBusiness, first.id(), requestId)
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String documentId = JsonPath.read(created, "$.documentId");
        registerDocument(owner, ownerBusiness, first.id(), requestId)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.documentId").value(documentId));
        registerDocument(owner, ownerBusiness, second.id(), requestId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_REQUEST_CONFLICT"));
        registerDocument(owner, ownerBusiness, first.id(), UUID.randomUUID())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_SOURCE_ALREADY_REGISTERED"));

        getDocument(outsider, ownerBusiness, UUID.fromString(documentId)).andExpect(status().isForbidden());
        getDocument(outsider, outsiderBusiness, UUID.fromString(documentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    void failedExtractionIsVisibleAndRetriesWithoutDuplicatingResults() throws Exception {
        Account owner = register("owner@example.com");
        UUID businessId = createBusiness(owner, "2026/444444/07");
        StoredFile source = uploadSource(owner, businessId, "invoice.pdf");
        String queued = registerDocument(owner, businessId, source.id(), UUID.randomUUID())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID documentId = UUID.fromString(JsonPath.read(queued, "$.documentId"));
        extractionProvider.failNext();

        assertThat(outboxWorker.pollOnce()).isEqualTo(1);
        getDocument(owner, businessId, documentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FAILED"))
                .andExpect(jsonPath("$.lastError").doesNotExist())
                .andExpect(jsonPath("$.extraction").doesNotExist());
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT last_error FROM document_record WHERE id = ?", String.class, documentId))
                .isEqualTo("Extraction failed (IllegalStateException)");

        jdbcTemplate.update("""
            UPDATE outbox_message
               SET available_at = CURRENT_TIMESTAMP - INTERVAL '1 minute'
             WHERE type = ? AND status = 'PENDING'
            """, DocumentExtractionRequested.TYPE);
        assertThat(outboxWorker.pollOnce()).isEqualTo(1);
        getDocument(owner, businessId, documentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PARSED"))
                .andExpect(jsonPath("$.processingAttempts").value(2));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM document_extraction", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void rejectsConfirmationBeforeParsingAndConflictingConfirmationRetries() throws Exception {
        Account owner = register("owner@example.com");
        UUID businessId = createBusiness(owner, "2026/555555/07");
        StoredFile source = uploadSource(owner, businessId, "invoice.pdf");
        String queued = registerDocument(owner, businessId, source.id(), UUID.randomUUID())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID documentId = UUID.fromString(JsonPath.read(queued, "$.documentId"));
        UUID confirmationRequest = UUID.randomUUID();

        confirm(owner, businessId, documentId, confirmationBody(confirmationRequest, "A", "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_READY_FOR_CONFIRMATION"));
        outboxWorker.pollOnce();
        confirm(owner, businessId, documentId, confirmationBody(confirmationRequest, "A", "1"))
                .andExpect(status().isOk());
        confirm(owner, businessId, documentId, confirmationBody(confirmationRequest, "B", "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_REQUEST_CONFLICT"));
    }

    private Account register(String email) {
        var tokens = authService.register(email, "correct-horse-battery", RegistrationType.BUSINESS_OWNER);
        return new Account(tokens.userId(), tokens.accessToken());
    }

    private UUID createBusiness(Account owner, String registrationNumber) {
        var onboarding = onboardingService.start(registrationNumber, owner.userId());
        return onboardingService.confirm(onboarding.id(), owner.userId()).id();
    }

    private StoredFile uploadSource(Account owner, UUID businessId, String filename) {
        return storage.upload(businessId, owner.userId(), FileCategory.INVOICE, filename, "application/pdf", PDF);
    }

    private org.springframework.test.web.servlet.ResultActions registerDocument(
            Account account, UUID businessId, UUID storedFileId, UUID requestId) throws Exception {
        String body = """
            {"storedFileId":"%s","type":"INVOICE","requestId":"%s"}
            """.formatted(storedFileId, requestId);
        return mockMvc.perform(post("/api/businesses/{businessId}/documents", businessId)
                .header(HttpHeaders.AUTHORIZATION, bearer(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions getDocument(
            Account account, UUID businessId, UUID documentId) throws Exception {
        return mockMvc.perform(get("/api/businesses/{businessId}/documents/{documentId}", businessId, documentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(account)));
    }

    private org.springframework.test.web.servlet.ResultActions confirm(
            Account account, UUID businessId, UUID documentId, String body) throws Exception {
        return mockMvc.perform(
                post("/api/businesses/{businessId}/documents/{documentId}/confirmations", businessId, documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(account))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private static String confirmationBody(UUID requestId, String supplier, String total) {
        return """
            {
              "requestId":"%s",
              "fields":[
                {"path":"supplier.name","value":"%s"},
                {"path":"total.value","value":"%s"}
              ]
            }
            """.formatted(requestId, supplier, total);
    }

    @SuppressWarnings("unchecked")
    private static String fieldValue(String json, String path, String fieldPath) {
        List<Map<String, Object>> fields = JsonPath.read(json, path);
        return fields.stream()
                .filter(field -> fieldPath.equals(field.get("path")))
                .map(field -> field.get("value").toString())
                .findFirst()
                .orElseThrow();
    }

    private long confirmedEventCount() {
        return applicationEvents.stream(PublishedEvent.class)
                .map(event -> ((PublishedEvent<?>) event).event())
                .filter(DocumentEvent.Confirmed.class::isInstance)
                .count();
    }

    private static String bearer(Account account) {
        return "Bearer " + account.accessToken();
    }

    private record Account(UUID userId, String accessToken) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class DocumentTestConfiguration {

        @Bean
        @Primary
        InMemoryObjectStorage inMemoryObjectStorage() {
            return new InMemoryObjectStorage();
        }

        @Bean
        @Primary
        MutableExtractionProvider mutableExtractionProvider() {
            return new MutableExtractionProvider();
        }
    }

    static class MutableExtractionProvider implements DocumentExtractionProvider {

        private final AtomicBoolean failNext = new AtomicBoolean();
        private int calls;

        @Override
        public String name() {
            return "test-document-extractor";
        }

        @Override
        public ExtractionResult extract(ExtractionRequest request) {
            calls++;
            if (failNext.compareAndSet(true, false)) {
                throw new IllegalStateException("provider unavailable");
            }
            assertThat(request.content()).startsWith('%', 'P', 'D', 'F', '-');
            return new ExtractionResult(
                    "test-1.0",
                    "test-result:" + request.documentId(),
                    List.of(
                            new ExtractedDocumentField(
                                    "supplier.name", "Demo Supplier", new BigDecimal("0.9800"), 1, "10,10,200,20"),
                            new ExtractedDocumentField(
                                    "total.value", "8500.00", new BigDecimal("0.9200"), 1, "200,500,100,20")));
        }

        void failNext() {
            failNext.set(true);
        }

        int calls() {
            return calls;
        }

        void reset() {
            failNext.set(false);
            calls = 0;
        }
    }
}
