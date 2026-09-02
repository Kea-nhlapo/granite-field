package za.co.trademesh.modules.delivery.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import za.co.trademesh.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
class DeliveryControllerIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void acceptsAuthenticatedVoiceSearchAndReturnsDetectedLanguage() throws Exception {
        String registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "email": "voice-search@example.test",
                              "password": "correct-horse-battery",
                              "accountType": "BUSINESS_OWNER"
                            }
                            """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String accessToken = JsonPath.read(registration, "$.accessToken");
        MockMultipartFile audio =
                new MockMultipartFile("audio", "request.wav", "audio/wav", "Ngifuna umhlinzeki wobisi".getBytes());

        MockMultipartHttpServletRequestBuilder request = multipart("/api/delivery/voice-search")
                .file(audio)
                .param("latitude", "-26.005")
                .param("longitude", "28.210");
        mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detectedLanguage").value("zu-ZA"))
                .andExpect(jsonPath("$.transcript").value("Ngifuna umhlinzeki wobisi"))
                .andExpect(jsonPath("$.suppliers").isArray());
    }
}
