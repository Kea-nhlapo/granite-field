package za.co.trademesh.modules.delivery.infrastructure;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.delivery.application.SpeechRecognitionClient;

@Component
@ConditionalOnProperty(prefix = "trademesh.delivery.search", name = "speech-provider", havingValue = "local")
class LocalSpeechRecognitionClient implements SpeechRecognitionClient {

    @Override
    public Transcript transcribe(byte[] audio, String contentType) {
        String text = new String(audio, StandardCharsets.UTF_8).strip();
        if (text.isBlank() || text.chars().anyMatch(character -> character == 0)) {
            throw new IllegalArgumentException("Local speech fixtures must contain UTF-8 text");
        }
        return new Transcript(detect(text), text);
    }

    private static String detect(String text) {
        String normalized = " " + text.toLowerCase(Locale.ROOT) + " ";
        if (containsAny(normalized, " ngifuna ", " umhlinzeki ", " ubisi ", " sawubona ")) {
            return "zu-ZA";
        }
        if (containsAny(normalized, " ndifuna ", " umthengisi ", " molo ")) {
            return "xh-ZA";
        }
        if (containsAny(normalized, " verskaffer ", " asseblief ", " ek soek ")) {
            return "af-ZA";
        }
        return "en-ZA";
    }

    private static boolean containsAny(String value, String... terms) {
        return java.util.Arrays.stream(terms).anyMatch(value::contains);
    }
}
