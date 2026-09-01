package za.co.trademesh.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import za.co.trademesh.modules.probe.config.ModuleProbeProperties;
import za.co.trademesh.shared.config.RuntimeProperties;
import za.co.trademesh.support.PostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// classes is restated because a @SpringBootTest here overrides the one on
// PostgresIntegrationTest, and the base class names the configuration
// explicitly so tests outside this package can find it.
@SpringBootTest(classes = TradeMeshApplication.class, properties = {
    "trademesh.runtime.environment=local",
    "trademesh.probe.name=module-scan-verified",
    "trademesh.security.jwt.secret=test-only-auth-secret-32-characters"
})
@AutoConfigureMockMvc
class TradeMeshApplicationTests extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RuntimeProperties runtimeProperties;

    @Autowired
    private ModuleProbeProperties moduleProbeProperties;

    @Test
    void startsWithSafeLocalDefaults() {
        assertThat(runtimeProperties.environment()).isEqualTo("local");
    }

    @Test
    void discoversConfigurationPropertiesOwnedByFeatureModules() {
        assertThat(moduleProbeProperties.name()).isEqualTo("module-scan-verified");
    }

    @Test
    void exposesHealthWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @WithMockUser
    void doesNotExposeOtherActuatorEndpoints() throws Exception {
        mockMvc.perform(get("/actuator/env"))
            .andExpect(status().isNotFound());
    }
}
