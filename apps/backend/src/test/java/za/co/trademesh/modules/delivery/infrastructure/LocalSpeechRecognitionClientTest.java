package za.co.trademesh.modules.delivery.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LocalSpeechRecognitionClientTest {

    private final LocalSpeechRecognitionClient client = new LocalSpeechRecognitionClient();

    @Test
    void detectsEnglishFixture() {
        var result = client.transcribe("I need a maize supplier".getBytes(StandardCharsets.UTF_8), "audio/wav");

        assertThat(result.languageCode()).isEqualTo("en-ZA");
        assertThat(result.text()).isEqualTo("I need a maize supplier");
    }

    @Test
    void detectsIsiZuluFixture() {
        var result = client.transcribe("Ngifuna umhlinzeki wobisi".getBytes(StandardCharsets.UTF_8), "audio/wav");

        assertThat(result.languageCode()).isEqualTo("zu-ZA");
        assertThat(result.text()).isEqualTo("Ngifuna umhlinzeki wobisi");
    }
}
