package za.co.trademesh.shared.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import za.co.trademesh.bootstrap.TradeMeshApplication;
import za.co.trademesh.support.PostgresIntegrationTest;

@SpringBootTest(
        classes = TradeMeshApplication.class,
        properties = "trademesh.security.jwt.secret=test-only-auth-secret-32-characters")
@AutoConfigureMockMvc
class OpenApiContractIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void committedContractMatchesTheRunningApplication() throws Exception {
        String actual = mockMvc.perform(get("/v3/api-docs/trademesh-v1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Path contract = Path.of("..", "..", "packages", "api-contracts", "openapi", "trademesh-v1.json");

        assertThat(objectMapper.readTree(Files.readString(contract))).isEqualTo(objectMapper.readTree(actual));
    }

    @Test
    void ordinaryTrustSchemaDoesNotExposeInternalRiskOrLocation() throws Exception {
        String content = mockMvc.perform(get("/v3/api-docs/trademesh-v1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode properties = objectMapper
                .readTree(content)
                .path("components")
                .path("schemas")
                .path("PublicSummaryResponse")
                .path("properties");

        assertThat(properties.has("riskScore")).isFalse();
        assertThat(properties.has("riskIndicators")).isFalse();
        assertThat(properties.has("latitude")).isFalse();
        assertThat(properties.has("longitude")).isFalse();
    }
}
