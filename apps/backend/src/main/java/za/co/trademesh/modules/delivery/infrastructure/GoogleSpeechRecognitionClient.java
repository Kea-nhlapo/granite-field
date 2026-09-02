package za.co.trademesh.modules.delivery.infrastructure;

import java.util.Base64;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import za.co.trademesh.modules.delivery.application.DeliverySearchProperties;
import za.co.trademesh.modules.delivery.application.SpeechRecognitionClient;

@Component
@ConditionalOnProperty(prefix = "trademesh.delivery.search", name = "speech-provider", havingValue = "google")
class GoogleSpeechRecognitionClient implements SpeechRecognitionClient {

    private final RestClient client;
    private final DeliverySearchProperties properties;

    GoogleSpeechRecognitionClient(RestClient.Builder builder, DeliverySearchProperties properties) {
        if (properties.speechEndpoint().isBlank()
                || properties.speechApiKey().isBlank()
                || properties.languageCodes().isEmpty()) {
            throw new IllegalStateException("Google Speech endpoint, API key, and language codes are required");
        }
        this.client = builder.baseUrl(properties.speechEndpoint()).build();
        this.properties = properties;
    }

    @Override
    public Transcript transcribe(byte[] audio, String contentType) {
        List<String> languages = properties.languageCodes();
        SpeechResponse response = client.post()
                .uri(builder ->
                        builder.queryParam("key", properties.speechApiKey()).build())
                .body(new SpeechRequest(
                        new RecognitionConfig(
                                languages.getFirst(), languages.stream().skip(1).toList(), true, "latest_short"),
                        new RecognitionAudio(Base64.getEncoder().encodeToString(audio))))
                .retrieve()
                .body(SpeechResponse.class);
        if (response == null || response.results() == null || response.results().isEmpty()) {
            throw new IllegalStateException("Google Speech returned no transcript");
        }
        RecognitionResult result = response.results().getFirst();
        if (result.alternatives() == null || result.alternatives().isEmpty()) {
            throw new IllegalStateException("Google Speech returned no transcript");
        }
        String language = result.languageCode() == null || result.languageCode().isBlank()
                ? languages.getFirst()
                : result.languageCode().strip();
        return new Transcript(language, result.alternatives().getFirst().transcript());
    }

    private record SpeechRequest(RecognitionConfig config, RecognitionAudio audio) {}

    private record RecognitionConfig(
            String languageCode,
            List<String> alternativeLanguageCodes,
            boolean enableAutomaticPunctuation,
            String model) {}

    private record RecognitionAudio(String content) {}

    private record SpeechResponse(List<RecognitionResult> results) {}

    private record RecognitionResult(List<RecognitionAlternative> alternatives, String languageCode) {}

    private record RecognitionAlternative(String transcript) {}
}
