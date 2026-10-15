package za.co.trademesh.modules.delivery.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import za.co.trademesh.modules.delivery.application.DeliverySearchProperties;

class GoogleSpeechRecognitionClientTest {

    private HttpServer server;
    private volatile String lastRequestBody;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void neverSendsAModelFieldGoogleMightRejectForTheRequestedLanguage() throws IOException {
        var client = clientFor(fakeGoogle(200, "{\"results\":[{\"alternatives\":[{\"transcript\":\"hello\"}]}]}"));

        client.transcribe(new byte[] {1, 2, 3}, "audio/wav");

        // Pinning a model (e.g. "latest_short") broke en-ZA and likely the other South African
        // locales on Google's real API — see GoogleSpeechRecognitionClient. The request must
        // never include a "model" key so Google can pick one that supports the language.
        assertThat(lastRequestBody).doesNotContain("\"model\"");
    }

    @Test
    void parsesTheFirstAlternativeAndDetectedLanguage() throws IOException {
        var client = clientFor(fakeGoogle(
                200, "{\"results\":[{\"languageCode\":\"zu-ZA\",\"alternatives\":[{\"transcript\":\"sawubona\"}]}]}"));

        var transcript = client.transcribe(new byte[] {1, 2, 3}, "audio/wav");

        assertThat(transcript.languageCode()).isEqualTo("zu-ZA");
        assertThat(transcript.text()).isEqualTo("sawubona");
    }

    @Test
    void failsWhenGoogleReturnsNoResults() throws IOException {
        var client = clientFor(fakeGoogle(200, "{\"results\":[]}"));

        assertThatThrownBy(() -> client.transcribe(new byte[] {1, 2, 3}, "audio/wav"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no transcript");
    }

    private GoogleSpeechRecognitionClient clientFor(String endpoint) {
        var properties = new DeliverySearchProperties(
                "google", endpoint, "test-key", List.of("en-ZA", "zu-ZA"), "local", "", "", 2_097_152, 10);
        return new GoogleSpeechRecognitionClient(RestClient.builder(), properties);
    }

    private String fakeGoogle(int status, String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/speech:recognize", exchange -> {
            lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.setExecutor(null);
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/speech:recognize";
    }
}
