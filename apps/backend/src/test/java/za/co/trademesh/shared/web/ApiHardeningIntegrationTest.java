package za.co.trademesh.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.trademesh.bootstrap.TradeMeshApplication;
import za.co.trademesh.shared.security.AccountRole;
import za.co.trademesh.shared.security.JwtTokenService;
import za.co.trademesh.support.PostgresIntegrationTest;

@SpringBootTest(
        classes = {TradeMeshApplication.class, ApiHardeningIntegrationTest.FailureController.class},
        properties = {
            "trademesh.security.jwt.secret=test-only-auth-secret-32-characters",
            "trademesh.web.rate-limits.login=1",
            "trademesh.web.rate-limits.invitations=1",
            "trademesh.web.rate-limits.uploads=1",
            "trademesh.web.rate-limits.telemetry=1",
            "trademesh.web.rate-limits.qr-validation=1",
            "trademesh.web.maximum-content-length=512B"
        })
@AutoConfigureMockMvc
class ApiHardeningIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry metrics;

    @Autowired
    private JwtTokenService tokens;

    @Test
    void returnsTrustedRequestIdSecurityHeadersAndMetrics() throws Exception {
        String requestId = UUID.randomUUID().toString();

        mockMvc.perform(get("/actuator/health").header(RequestContext.REQUEST_ID_HEADER, requestId))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestContext.REQUEST_ID_HEADER, requestId))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"));

        assertThat(metrics.find("trademesh.http.request.duration").timers()).isNotEmpty();
    }

    @Test
    void replacesUntrustedRequestIdsWithServerGeneratedUuids() throws Exception {
        String returned = mockMvc.perform(
                        get("/actuator/health").header(RequestContext.REQUEST_ID_HEADER, "not-a-uuid"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader(RequestContext.REQUEST_ID_HEADER);

        assertThat(UUID.fromString(returned)).isNotNull();
    }

    @Test
    void allowsOnlyConfiguredBrowserOrigins() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));

        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://attacker.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void returnsOneSafeProblemFormatForUnexpectedAndMalformedRequests() throws Exception {
        String requestId = UUID.randomUUID().toString();

        String failure = mockMvc.perform(
                        get("/api/auth/hardening-test/failure").header(RequestContext.REQUEST_ID_HEADER, requestId))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.instance").value("/api"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(failure).doesNotContain("secret-refresh-token", "supplier@example.com", "-26.2041");

        String invalid = mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.41");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"private@example.com\",\"password\":}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.requestId").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(invalid).doesNotContain("private@example.com", "HttpMessageNotReadableException", "stackTrace");
    }

    @Test
    void rateLimitsLoginAndRejectsOversizedRequestsBeforeParsing() throws Exception {
        var login = post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr("192.0.2.42");
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"unknown@example.com\",\"password\":\"long-enough-password\"}");

        mockMvc.perform(login).andExpect(status().isUnauthorized());
        mockMvc.perform(login)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("x".repeat(513)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("REQUEST_TOO_LARGE"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("protectedOperations")
    void rateLimitsEverySensitiveOperation(
            String ignoredName, MockHttpServletRequestBuilder request, String remoteAddress) throws Exception {
        request.with(servletRequest -> {
            servletRequest.setRemoteAddr(remoteAddress);
            return servletRequest;
        });

        mockMvc.perform(request);
        mockMvc.perform(request)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void exposesReadinessButProtectsMetricsAsAdministratorOnly() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());

        String businessToken = tokens.issue(UUID.randomUUID(), Set.of(AccountRole.BUSINESS_OWNER))
                .value();
        mockMvc.perform(get("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, "Bearer " + businessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        String adminToken = tokens.issue(UUID.randomUUID(), Set.of(AccountRole.ADMINISTRATOR))
                .value();
        mockMvc.perform(get("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
    }

    @RestController
    static class FailureController {

        @GetMapping("/api/auth/hardening-test/failure")
        void fail() {
            throw new IllegalStateException(
                    "secret-refresh-token supplier@example.com document text latitude=-26.2041");
        }
    }

    static Stream<Arguments> protectedOperations() {
        UUID businessId = UUID.randomUUID();
        return Stream.of(
                Arguments.of(
                        "invitation creation",
                        post("/api/businesses/{businessId}/supplier-invitations", businessId),
                        "192.0.2.51"),
                Arguments.of(
                        "guest invitation token",
                        get("/api/supplier-invitations/guest/not-a-real-token"),
                        "192.0.2.52"),
                Arguments.of("file upload", post("/api/businesses/{businessId}/files", businessId), "192.0.2.53"),
                Arguments.of("telemetry ingestion", post("/api/telemetry/readings"), "192.0.2.54"),
                Arguments.of("QR validation", post("/api/handovers/confirmations"), "192.0.2.55"));
    }
}
