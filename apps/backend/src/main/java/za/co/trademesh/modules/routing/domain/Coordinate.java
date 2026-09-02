package za.co.trademesh.modules.routing.domain;

/**
 * A WGS84 point, matching the geography(Point, 4326) convention the database
 * foundation established in issue #2.
 */
public record Coordinate(double latitude, double longitude) {

    public Coordinate {
        // Checked before the range comparisons: every comparison against NaN is
        // false, so a NaN would slip through them and only surface much later as
        // a zero distance, far from the coordinate that caused it.
        if (!Double.isFinite(latitude)) {
            throw new IllegalArgumentException("latitude must be a finite number, was " + latitude);
        }
        if (!Double.isFinite(longitude)) {
            throw new IllegalArgumentException("longitude must be a finite number, was " + longitude);
        }
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("latitude must be between -90 and 90, was " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("longitude must be between -180 and 180, was " + longitude);
        }
    }
}
