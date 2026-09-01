package za.co.trademesh.modules.routing.domain;

/**
 * A WGS84 point, matching the geography(Point, 4326) convention the database
 * foundation established in issue #2.
 */
public record Coordinate(double latitude, double longitude) {

    public Coordinate {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("latitude must be between -90 and 90, was " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("longitude must be between -180 and 180, was " + longitude);
        }
    }
}
