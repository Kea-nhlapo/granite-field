package za.co.trademesh.modules.routing.application;

import java.util.List;
import za.co.trademesh.modules.routing.domain.GeoPoint;

/** Google-compatible encoded polyline support for provider and frontend boundaries. */
public final class EncodedPolyline {

    private EncodedPolyline() {}

    public static String encode(List<GeoPoint> points) {
        StringBuilder encoded = new StringBuilder();
        long previousLatitude = 0;
        long previousLongitude = 0;
        for (GeoPoint point : points) {
            long latitude = Math.round(point.latitude() * 100_000);
            long longitude = Math.round(point.longitude() * 100_000);
            append(latitude - previousLatitude, encoded);
            append(longitude - previousLongitude, encoded);
            previousLatitude = latitude;
            previousLongitude = longitude;
        }
        return encoded.toString();
    }

    public static List<GeoPoint> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("Encoded polyline is required");
        }
        java.util.ArrayList<GeoPoint> points = new java.util.ArrayList<>();
        int index = 0;
        long latitude = 0;
        long longitude = 0;
        while (index < encoded.length()) {
            Decoded latitudeDelta = read(encoded, index);
            index = latitudeDelta.nextIndex();
            Decoded longitudeDelta = read(encoded, index);
            index = longitudeDelta.nextIndex();
            latitude += latitudeDelta.value();
            longitude += longitudeDelta.value();
            points.add(new GeoPoint(null, latitude / 100_000.0, longitude / 100_000.0));
        }
        if (points.size() < 2) {
            throw new IllegalArgumentException("Encoded polyline must contain at least two points");
        }
        return List.copyOf(points);
    }

    private static void append(long value, StringBuilder encoded) {
        long shifted = value < 0 ? ~(value << 1) : value << 1;
        while (shifted >= 0x20) {
            encoded.append((char) ((0x20 | (shifted & 0x1f)) + 63));
            shifted >>= 5;
        }
        encoded.append((char) (shifted + 63));
    }

    private static Decoded read(String encoded, int start) {
        long result = 0;
        int shift = 0;
        int index = start;
        int value;
        do {
            if (index >= encoded.length() || shift > 60) {
                throw new IllegalArgumentException("Encoded polyline is malformed");
            }
            value = encoded.charAt(index++) - 63;
            if (value < 0 || value > 0x5f) {
                throw new IllegalArgumentException("Encoded polyline is malformed");
            }
            result |= (long) (value & 0x1f) << shift;
            shift += 5;
        } while (value >= 0x20);
        long decoded = (result & 1) == 0 ? result >> 1 : ~(result >> 1);
        return new Decoded(decoded, index);
    }

    private record Decoded(long value, int nextIndex) {}
}
