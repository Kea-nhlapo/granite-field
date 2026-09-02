package za.co.trademesh.modules.delivery.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.delivery.application.SpeechRecognitionClient;

/** Fallback speech client: the application starts, transcription fails loudly. */
@Component
@ConditionalOnProperty(
        prefix = "trademesh.delivery.search",
        name = "speech-provider",
        havingValue = "unconfigured",
        matchIfMissing = true)
class UnconfiguredSpeechRecognitionClient implements SpeechRecognitionClient {

    @Override
    public Transcript transcribe(byte[] audio, String contentType) {
        throw new IllegalStateException(
                "No speech provider is configured; set trademesh.delivery.search.speech-provider");
    }
}
