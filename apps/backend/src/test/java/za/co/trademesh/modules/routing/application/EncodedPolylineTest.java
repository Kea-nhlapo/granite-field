package za.co.trademesh.modules.routing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import za.co.trademesh.modules.routing.domain.GeoPoint;

class EncodedPolylineTest {

    @Test
    void matchesTheGooglePolylineFormatAndRoundTripsCoordinates() {
        List<GeoPoint> points = List.of(
                new GeoPoint(null, 38.5, -120.2),
                new GeoPoint(null, 40.7, -120.95),
                new GeoPoint(null, 43.252, -126.453));

        String encoded = EncodedPolyline.encode(points);

        assertThat(encoded).isEqualTo("_p~iF~ps|U_ulLnnqC_mqNvxq`@");
        assertThat(EncodedPolyline.decode(encoded))
                .extracting(GeoPoint::latitude, GeoPoint::longitude)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(38.5, -120.2),
                        org.assertj.core.groups.Tuple.tuple(40.7, -120.95),
                        org.assertj.core.groups.Tuple.tuple(43.252, -126.453));
    }

    @Test
    void rejectsMalformedInput() {
        assertThatThrownBy(() -> EncodedPolyline.decode("?")).isInstanceOf(IllegalArgumentException.class);
    }
}
