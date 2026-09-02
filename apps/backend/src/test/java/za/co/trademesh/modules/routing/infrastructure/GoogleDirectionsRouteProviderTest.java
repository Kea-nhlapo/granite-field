package za.co.trademesh.modules.routing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import za.co.trademesh.modules.routing.application.EncodedPolyline;
import za.co.trademesh.modules.routing.application.RouteProvider;
import za.co.trademesh.modules.routing.application.RouteProviderException;
import za.co.trademesh.modules.routing.application.RoutingProviderProperties;
import za.co.trademesh.modules.routing.domain.GeoPoint;
import za.co.trademesh.modules.routing.domain.RouteAvoidance;
import za.co.trademesh.modules.routing.domain.VehicleLimits;

class GoogleDirectionsRouteProviderTest {

    @Test
    void mapsGoogleDistanceDurationAndPolylineWithoutExposingTheKey() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var provider = new GoogleDirectionsRouteProvider(builder, properties());
        RouteProvider.ProviderRequest request = request();
        String polyline = EncodedPolyline.encode(
                List.of(request.origin(), new GeoPoint(null, -25.99, 28.12), request.destination()));
        String response = """
            {
              "status":"OK",
              "routes":[{
                "summary":"N1",
                "overview_polyline":{"points":"%s"},
                "legs":[{
                  "start_address":"Johannesburg",
                  "end_address":"Pretoria",
                  "distance":{"value":61234},
                  "duration":{"value":3100}
                }]
              }]
            }
            """.formatted(json(polyline));
        server.expect(requestTo(containsString("key=test-key")))
                .andExpect(requestTo(containsString("avoid=tolls")))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        var result = provider.calculate(request);

        assertThat(result.providerName()).isEqualTo("google-directions");
        assertThat(result.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.geometry().getFirst()).isEqualTo(request.origin());
            assertThat(candidate.geometry().getLast()).isEqualTo(request.destination());
            assertThat(candidate.distanceMetres()).isEqualTo(61_234);
            assertThat(candidate.durationSeconds()).isEqualTo(3_100);
        });
        server.verify();
    }

    @Test
    void turnsGoogleStatusFailuresIntoSafeProviderErrors() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var provider = new GoogleDirectionsRouteProvider(builder, properties());
        server.expect(requestTo(containsString("key=test-key")))
                .andRespond(withSuccess("{\"status\":\"ZERO_RESULTS\",\"routes\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.calculate(request()))
                .isInstanceOf(RouteProviderException.class)
                .extracting(failure -> ((RouteProviderException) failure).code())
                .isEqualTo("GOOGLE_DIRECTIONS_ZERO_RESULTS");
    }

    private static RoutingProviderProperties properties() {
        return new RoutingProviderProperties(
                "google", "https://maps.test/directions", "test-key", Duration.ofSeconds(2), 20, 3);
    }

    private static RouteProvider.ProviderRequest request() {
        return new RouteProvider.ProviderRequest(
                new GeoPoint("Johannesburg", -26.2041, 28.0473),
                new GeoPoint("Pretoria", -25.7479, 28.2293),
                List.of(),
                new VehicleLimits(
                        new BigDecimal("5000.000"),
                        new BigDecimal("4.200"),
                        new BigDecimal("2.500"),
                        new BigDecimal("12.000")),
                List.of(RouteAvoidance.TOLLS, RouteAvoidance.UNPAVED_ROADS),
                3);
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
