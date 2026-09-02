package za.co.trademesh.modules.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.trademesh.modules.supplier.application.SupplierDirectory;

class VoiceSupplierSearchServiceTest {

    @Test
    void ranksCandidatesUsingPublicTrustAndMeasuredDistance() {
        UUID trustedFar = UUID.randomUUID();
        UUID near = UUID.randomUUID();
        SupplierDirectory directory = mock(SupplierDirectory.class);
        when(directory.search("milk", 10))
                .thenReturn(List.of(
                        new SupplierDirectory.SearchResult(
                                trustedFar,
                                UUID.randomUUID(),
                                "Trusted Dairy",
                                "Pretoria",
                                new BigDecimal("4.8"),
                                new BigDecimal("0.98")),
                        new SupplierDirectory.SearchResult(
                                near,
                                UUID.randomUUID(),
                                "Nearby Dairy",
                                "Tembisa",
                                new BigDecimal("4.0"),
                                new BigDecimal("0.90"))));
        SpeechRecognitionClient speech =
                (audio, contentType) -> new SpeechRecognitionClient.Transcript("en-ZA", "milk");
        SupplierDistanceClient distance = (latitude, longitude, destinations) -> Map.of(
                trustedFar, new SupplierDistanceClient.Distance(45_000, 3_000),
                near, new SupplierDistanceClient.Distance(1_000, 180));
        var properties = new DeliverySearchProperties(
                "local", "", "", List.of("en-ZA", "zu-ZA"), "local", "", "", 2_097_152, 10);
        var service = new VoiceSupplierSearchService(speech, distance, directory, properties);

        var result = service.search("milk".getBytes(StandardCharsets.UTF_8), "audio/wav", -26.0, 28.2);

        assertThat(result.detectedLanguage()).isEqualTo("en-ZA");
        assertThat(result.suppliers())
                .extracting(VoiceSupplierSearchService.RankedSupplier::supplierProfileId)
                .containsExactly(near, trustedFar);
        assertThat(result.suppliers().getFirst().distanceMetres()).isEqualTo(1_000);
    }
}
