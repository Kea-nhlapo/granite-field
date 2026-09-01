package za.co.trademesh.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import za.co.trademesh.shared.config.RuntimeProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TradeMeshApplicationTests {

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
