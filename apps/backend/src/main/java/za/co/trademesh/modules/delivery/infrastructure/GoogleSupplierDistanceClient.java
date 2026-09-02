package za.co.trademesh.modules.delivery.infrastructure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import za.co.trademesh.modules.delivery.application.DeliverySearchProperties;
import za.co.trademesh.modules.delivery.application.SupplierDistanceClient;

@Component
@ConditionalOnProperty(prefix = "trademesh.delivery.search", name = "distance-provider", havingValue = "google")
class GoogleSupplierDistanceClient implements SupplierDistanceClient {

    private final RestClient client;
    private final DeliverySearchProperties properties;

    GoogleSupplierDistanceClient(RestClient.Builder builder, DeliverySearchProperties properties) {
        if (properties.distanceEndpoint().isBlank() || properties.mapsApiKey().isBlank()) {
            throw new IllegalStateException("Google distance endpoint and API key are required");
        }
        this.client = builder.baseUrl(properties.distanceEndpoint()).build();
        this.properties = properties;
    }

    @Override
    public Map<UUID, Distance> distances(
            double originLatitude, double originLongitude, List<Destination> destinations) {
        if (destinations.isEmpty()) {
            return Map.of();
        }
        String destinationAddresses =
                destinations.stream().map(Destination::address).collect(Collectors.joining("|"));
        DistanceResponse response = client.get()
                .uri(builder -> builder.queryParam("origins", originLatitude + "," + originLongitude)
                        .queryParam("destinations", destinationAddresses)
                        .queryParam("units", "metric")
                        .queryParam("key", properties.mapsApiKey())
                        .build())
                .retrieve()
                .body(DistanceResponse.class);
        if (response == null
                || !"OK".equals(response.status())
                || response.rows() == null
                || response.rows().isEmpty()
                || response.rows().getFirst().elements() == null) {
            throw new IllegalStateException("Google distance matrix returned no usable result");
        }
        List<Element> elements = response.rows().getFirst().elements();
        Map<UUID, Distance> result = new LinkedHashMap<>();
        for (int index = 0; index < Math.min(destinations.size(), elements.size()); index++) {
            Element element = elements.get(index);
            if ("OK".equals(element.status()) && element.distance() != null && element.duration() != null) {
                result.put(
                        destinations.get(index).supplierProfileId(),
                        new Distance(
                                element.distance().value(), element.duration().value()));
            }
        }
        return Map.copyOf(result);
    }

    private record DistanceResponse(String status, List<Row> rows) {}

    private record Row(List<Element> elements) {}

    private record Element(String status, Measure distance, Measure duration) {}

    private record Measure(long value) {}
}
