package za.co.trademesh.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import za.co.trademesh.shared.config.RuntimeProperties;
import za.co.trademesh.support.PostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TradeMeshApplicationTests extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RuntimeProperties runtimeProperties;

    @Test
    void startsWithSafeLocalDefaults() {
        assertThat(runtimeProperties.environment()).isEqualTo("local");
    }

    @Test
    void exposesOnlyTheHealthEndpointByDefault() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/env"))
            .andExpect(status().isNotFound());
    }
}
