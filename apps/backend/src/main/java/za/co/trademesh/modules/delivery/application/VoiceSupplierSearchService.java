package za.co.trademesh.modules.delivery.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import za.co.trademesh.modules.supplier.application.SupplierDirectory;

@Service
public class VoiceSupplierSearchService {

    private static final BigDecimal MAX_RATING = BigDecimal.valueOf(5);
    private static final BigDecimal TRUST_WEIGHT = new BigDecimal("0.70");
    private static final BigDecimal DISTANCE_WEIGHT = new BigDecimal("0.30");
    private static final BigDecimal DISTANCE_SCALE_METRES = new BigDecimal("50000");

    private final SpeechRecognitionClient speech;
    private final SupplierDistanceClient distances;
    private final SupplierDirectory suppliers;
    private final DeliverySearchProperties properties;

    public VoiceSupplierSearchService(
            SpeechRecognitionClient speech,
            SupplierDistanceClient distances,
            SupplierDirectory suppliers,
            DeliverySearchProperties properties) {
        this.speech = speech;
        this.distances = distances;
        this.suppliers = suppliers;
        this.properties = properties;
    }

    public SearchResult search(byte[] audio, String contentType, double latitude, double longitude) {
        if (audio == null
                || audio.length == 0
                || audio.length > properties.maximumAudioBytes()
                || !Double.isFinite(latitude)
                || !Double.isFinite(longitude)
                || latitude < -90
                || latitude > 90
                || longitude < -180
                || longitude > 180) {
            throw DeliveryException.invalidVoiceRequest();
        }
        SpeechRecognitionClient.Transcript transcript;
        try {
            transcript = speech.transcribe(audio, contentType == null ? "application/octet-stream" : contentType);
        } catch (RuntimeException providerFailure) {
            throw DeliveryException.voiceProviderUnavailable();
        }
        if (transcript == null || transcript.text() == null || transcript.text().isBlank()) {
            throw DeliveryException.voiceProviderUnavailable();
        }

        List<SupplierDirectory.SearchResult> candidates;
        try {
            candidates = suppliers.search(transcript.text(), properties.supplierLimit());
        } catch (IllegalArgumentException invalidTranscript) {
            throw DeliveryException.invalidVoiceRequest();
        }
        Map<java.util.UUID, SupplierDistanceClient.Distance> measured = safeDistances(latitude, longitude, candidates);

        List<RankedSupplier> ranked = candidates.stream()
                .map(candidate -> ranked(candidate, measured.get(candidate.supplierProfileId())))
                .sorted(Comparator.comparing(RankedSupplier::rankingScore)
                        .reversed()
                        .thenComparing(RankedSupplier::displayName)
                        .thenComparing(RankedSupplier::supplierProfileId))
                .toList();
        return new SearchResult(transcript.languageCode(), transcript.text().strip(), ranked);
    }

    private Map<java.util.UUID, SupplierDistanceClient.Distance> safeDistances(
            double latitude, double longitude, List<SupplierDirectory.SearchResult> candidates) {
        try {
            return distances.distances(
                    latitude,
                    longitude,
                    candidates.stream()
                            .map(candidate -> new SupplierDistanceClient.Destination(
                                    candidate.supplierProfileId(), candidate.registeredAddress()))
                            .toList());
        } catch (RuntimeException providerFailure) {
            return Map.of();
        }
    }

    private static RankedSupplier ranked(
            SupplierDirectory.SearchResult candidate, SupplierDistanceClient.Distance distance) {
        BigDecimal trust = candidate.averageRating() == null
                ? BigDecimal.ZERO
                : candidate
                        .averageRating()
                        .max(BigDecimal.ZERO)
                        .min(MAX_RATING)
                        .divide(MAX_RATING, 6, RoundingMode.HALF_UP);
        BigDecimal proximity = distance == null
                ? BigDecimal.ZERO
                : BigDecimal.ONE
                        .subtract(BigDecimal.valueOf(distance.metres())
                                .divide(DISTANCE_SCALE_METRES, 6, RoundingMode.HALF_UP))
                        .max(BigDecimal.ZERO);
        BigDecimal score = trust.multiply(TRUST_WEIGHT)
                .add(proximity.multiply(DISTANCE_WEIGHT))
                .setScale(6, RoundingMode.HALF_UP);
        return new RankedSupplier(
                candidate.supplierProfileId(),
                candidate.businessId(),
                candidate.displayName(),
                candidate.averageRating(),
                candidate.successfulDeliveryRate(),
                distance == null ? null : distance.metres(),
                distance == null ? null : distance.durationSeconds(),
                score);
    }

    public record SearchResult(String detectedLanguage, String transcript, List<RankedSupplier> suppliers) {}

    public record RankedSupplier(
            java.util.UUID supplierProfileId,
            java.util.UUID businessId,
            String displayName,
            BigDecimal averageRating,
            BigDecimal successfulDeliveryRate,
            Long distanceMetres,
            Long durationSeconds,
            BigDecimal rankingScore) {}
}
