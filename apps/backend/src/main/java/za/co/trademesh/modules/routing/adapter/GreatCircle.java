package za.co.trademesh.modules.routing.adapter;

import za.co.trademesh.modules.routing.domain.Coordinate;

import java.util.List;

/** Haversine distance on the IUGG mean Earth radius. */
final class GreatCircle {

    private static final double EARTH_RADIUS_METRES = 6_371_008.8;

    private GreatCircle() {
    }

    static long metresBetween(Coordinate from, Coordinate to) {
        double deltaLatitude = Math.toRadians(to.latitude() - from.latitude());
        double deltaLongitude = Math.toRadians(to.longitude() - from.longitude());
        double fromLatitude = Math.toRadians(from.latitude());
        double toLatitude = Math.toRadians(to.latitude());

        double a = Math.pow(Math.sin(deltaLatitude / 2), 2)
            + Math.pow(Math.sin(deltaLongitude / 2), 2) * Math.cos(fromLatitude) * Math.cos(toLatitude);

        return Math.round(2 * EARTH_RADIUS_METRES * Math.asin(Math.sqrt(a)));
    }

    static long metresAlong(List<Coordinate> stops) {
        long total = 0;
        for (int i = 0; i < stops.size() - 1; i++) {
            total += metresBetween(stops.get(i), stops.get(i + 1));
        }
        return total;
    }
}
