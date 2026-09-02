package za.co.trademesh.modules.delivery.application;

public interface SpeechRecognitionClient {

    Transcript transcribe(byte[] audio, String contentType);

    record Transcript(String languageCode, String text) {}
}
