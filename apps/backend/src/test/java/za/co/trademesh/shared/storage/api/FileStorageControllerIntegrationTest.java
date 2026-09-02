package za.co.trademesh.shared.storage.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import za.co.trademesh.modules.access.application.AuthService;
import za.co.trademesh.modules.access.domain.RegistrationType;
import za.co.trademesh.modules.business.application.RegisteredBusinessOnboardingService;
import za.co.trademesh.shared.storage.FileScanStatus;
import za.co.trademesh.shared.storage.FileScanner;
import za.co.trademesh.shared.storage.support.InMemoryObjectStorage;
import za.co.trademesh.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
@Import(FileStorageControllerIntegrationTest.StorageTestConfiguration.class)
class FileStorageControllerIntegrationTest extends PostgresIntegrationTest {

    private static final byte[] PDF = "%PDF-1.7\ntrade evidence".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private RegisteredBusinessOnboardingService onboardingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InMemoryObjectStorage objectStorage;

    @Autowired
    private MutableFileScanner scanner;

    @BeforeEach
    @AfterEach
    void cleanState() {
        objectStorage.clear();
        scanner.result(FileScanStatus.CLEAN);
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
    void storesValidatedBytesSeparatelyAndIssuesOwnerScopedShortLivedDownloads() throws Exception {
        Account owner = register("owner@example.com");
        UUID businessId = createBusiness(owner, "2024/666666/07");

        String response = upload(owner, businessId, "invoice.pdf", "application/pdf", PDF)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.businessId").value(businessId.toString()))
                .andExpect(jsonPath("$.category").value("INVOICE"))
                .andExpect(jsonPath("$.scanStatus").value("CLEAN"))
                .andExpect(jsonPath("$.storageStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.objectKey").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID fileId = UUID.fromString(JsonPath.read(response, "$.fileId"));

        String objectKey =
                jdbcTemplate.queryForObject("SELECT object_key FROM stored_file WHERE id = ?", String.class, fileId);
        assertThat(objectKey)
                .matches("objects/[0-9]{4}/[0-9]{2}/[0-9a-f-]{36}\\.pdf")
                .doesNotContain("invoice")
                .doesNotContain(businessId.toString())
                .doesNotContain("owner");
        assertThat(objectStorage.content(objectKey)).containsExactly(PDF);

        mockMvc.perform(get("/api/businesses/{businessId}/files/{fileId}", businessId, fileId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value(fileId.toString()))
                .andExpect(jsonPath("$.originalFilename").value("invoice.pdf"));

        mockMvc.perform(get("/api/businesses/{businessId}/files/{fileId}/download", businessId, fileId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("https://storage.test/")))
                .andExpect(jsonPath("$.expiresAt").isString());
    }

    @Test
    void hidesFilesFromOtherTenants() throws Exception {
        Account owner = register("owner@example.com");
        Account outsider = register("outsider@example.com");
        UUID ownerBusiness = createBusiness(owner, "2024/777777/07");
        UUID outsiderBusiness = createBusiness(outsider, "2024/888888/07");
        String response = upload(owner, ownerBusiness, "proof.pdf", "application/pdf", PDF)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID fileId = UUID.fromString(JsonPath.read(response, "$.fileId"));

        mockMvc.perform(get("/api/businesses/{businessId}/files/{fileId}", ownerBusiness, fileId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider.accessToken())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/businesses/{businessId}/files/{fileId}", outsiderBusiness, fileId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider.accessToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FILE_NOT_FOUND"));
    }

    @Test
    void rejectsUnsafeNamesTypeMismatchesAndScannerFailuresBeforeWritingBytes() throws Exception {
        Account owner = register("owner@example.com");
        UUID businessId = createBusiness(owner, "2024/999999/07");

        upload(owner, businessId, "../invoice.pdf", "application/pdf", PDF)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_NAME"));
        upload(owner, businessId, "invoice.exe", "application/pdf", PDF)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_EXTENSION_MISMATCH"));
        upload(owner, businessId, "invoice.png", "image/png", PDF)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_SIGNATURE_MISMATCH"));
        upload(owner, businessId, "invoice.txt", "text/plain", PDF)
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));

        scanner.result(FileScanStatus.INFECTED);
        upload(owner, businessId, "invoice.pdf", "application/pdf", PDF)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("FILE_SCAN_REJECTED"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM stored_file", Integer.class))
                .isZero();
        assertThat(objectStorage.size()).isZero();
    }

    @Test
    void recordsFailedMetadataWhenObjectStorageIsUnavailable() throws Exception {
        Account owner = register("owner@example.com");
        UUID businessId = createBusiness(owner, "2025/101010/07");
        objectStorage.failWrites(true);

        upload(owner, businessId, "invoice.pdf", "application/pdf", PDF)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("OBJECT_STORAGE_UNAVAILABLE"));

        assertThat(jdbcTemplate.queryForObject("SELECT storage_status FROM stored_file", String.class))
                .isEqualTo("FAILED");
        assertThat(objectStorage.size()).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions upload(
            Account account, UUID businessId, String filename, String contentType, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, contentType, content);
        return mockMvc.perform(multipart("/api/businesses/{businessId}/files", businessId)
                .file(file)
                .param("category", "INVOICE")
                .header(HttpHeaders.AUTHORIZATION, bearer(account.accessToken())));
    }

    private Account register(String email) {
        AuthService.AuthTokens tokens =
                authService.register(email, "correct-horse-battery", RegistrationType.BUSINESS_OWNER);
        return new Account(tokens.userId(), tokens.accessToken());
    }

    private UUID createBusiness(Account owner, String registrationNumber) {
        var onboarding = onboardingService.start(registrationNumber, owner.userId());
        return onboardingService.confirm(onboarding.id(), owner.userId()).id();
    }

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private record Account(UUID userId, String accessToken) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class StorageTestConfiguration {

        @Bean
        @Primary
        InMemoryObjectStorage inMemoryObjectStorage() {
            return new InMemoryObjectStorage();
        }

        @Bean
        @Primary
        MutableFileScanner mutableFileScanner() {
            return new MutableFileScanner();
        }
    }

    static class MutableFileScanner implements FileScanner {

        private volatile FileScanStatus result = FileScanStatus.CLEAN;

        @Override
        public FileScanStatus scan(String filename, String contentType, byte[] content) {
            return result;
        }

        void result(FileScanStatus result) {
            this.result = result;
        }
    }
}
