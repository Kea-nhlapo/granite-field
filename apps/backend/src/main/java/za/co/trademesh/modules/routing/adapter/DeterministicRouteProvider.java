package za.co.trademesh.modules.routing.adapter;

import za.co.trademesh.modules.routing.domain.Avoidance;
import za.co.trademesh.modules.routing.domain.CandidateRoute;
import za.co.trademesh.modules.routing.domain.Coordinate;
import za.co.trademesh.modules.routing.domain.Money;
import za.co.trademesh.modules.routing.domain.RouteCandidateSet;
import za.co.trademesh.modules.routing.domain.RouteProviderException;
import za.co.trademesh.modules.routing.domain.RouteRequest;
import za.co.trademesh.modules.routing.domain.RouteSegment;
import za.co.trademesh.modules.routing.port.RouteProvider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * A local routing engine that needs no network and returns the same candidates
 * for the same request on every run, in every JVM. Issue #17 develops its
 * scoring against this, so stability across processes is the point.
 *
 * <p>Determinism rules observed here: seed from a SHA-256 of a canonical string
 * form of the request — never Object.hashCode, which varies by JVM — and use no
 * wall-clock, no unseeded random, and no hash-ordered iteration.
 */
public class DeterministicRouteProvider implements RouteProvider {

    public static final String PROVIDER_NAME = "deterministic-local";
    public static final String PROVIDER_VERSION = "1.0.0";

    private static final int MINIMUM_CANDIDATES = 2;
    private static final double AVERAGE_SPEED_METRES_PER_SECOND = 22.2;
    private static final BigDecimal TOLL_RAND_PER_KILOMETRE = new BigDecimal("0.85");
    private static final String TOLL_CURRENCY = "ZAR";

    @Override
    public RouteCandidateSet findCandidates(RouteRequest request) {
        byte[] seed = seedOf(request);
        int candidateCount = MINIMUM_CANDIDATES + Math.floorMod(seed[0], 2);
        long directDistance = GreatCircle.metresAlong(request.orderedStops());
        boolean avoidsTolls = request.avoidances().contains(Avoidance.TOLLS);

        List<CandidateRoute> candidates = new ArrayList<>(candidateCount);
        for (int index = 0; index < candidateCount; index++) {
            candidates.add(candidate(request, seed, index, directDistance, avoidsTolls));
        }
        return RouteCandidateSet.of(request, List.copyOf(candidates));
    }

    private CandidateRoute candidate(
        RouteRequest request, byte[] seed, int index, long directDistance, boolean avoidsTolls) {

        double detourFactor = 1.05 + (index * 0.07) + (Math.floorMod(seed[index + 1], 16) / 1000.0);
        long distanceMetres = Math.max(1, Math.round(directDistance * detourFactor));
        Duration duration = Duration.ofSeconds(
            Math.max(1, Math.round(distanceMetres / AVERAGE_SPEED_METRES_PER_SECOND)));

        Optional<Money> toll = avoidsTolls ? Optional.empty() : Optional.of(tollFor(distanceMetres));

        return new CandidateRoute(
            candidateId(request, index),
            segments(request, index, distanceMetres, duration),
            distanceMetres,
            duration,
            toll,
            PROVIDER_NAME,
            PROVIDER_VERSION,
            false);
    }

    private static Money tollFor(long distanceMetres) {
        BigDecimal kilometres = BigDecimal.valueOf(distanceMetres).movePointLeft(3);
        return Money.of(
            TOLL_RAND_PER_KILOMETRE.multiply(kilometres).setScale(2, RoundingMode.HALF_UP),
            TOLL_CURRENCY);
    }

    private List<RouteSegment> segments(
        RouteRequest request, int index, long distanceMetres, Duration duration) {

        List<Coordinate> stops = request.orderedStops();
        int legs = stops.size() - 1;
        List<RouteSegment> segments = new ArrayList<>(legs);

        for (int leg = 0; leg < legs; leg++) {
            Coordinate from = stops.get(leg);
            Coordinate to = stops.get(leg + 1);

            segments.add(new RouteSegment(
                List.of(from, wobbledMidpoint(from, to, index), to),
                Math.max(1, distanceMetres / legs),
                duration.dividedBy(legs)));
        }
        return segments;
    }

    /**
     * A midpoint nudged aside, differently per candidate, so candidates are
     * distinguishable geometry rather than the same straight line repeated.
     */
    private Coordinate wobbledMidpoint(Coordinate from, Coordinate to, int index) {
        double offset = 0.01 * (index + 1);
        return new Coordinate(
            clamp((from.latitude() + to.latitude()) / 2 + offset, 90),
            clamp((from.longitude() + to.longitude()) / 2 - offset, 180));
    }

    private static double clamp(double value, double bound) {
        return Math.max(-bound, Math.min(bound, value));
    }

    private String candidateId(RouteRequest request, int index) {
        return UUID.nameUUIDFromBytes(
            (canonicalForm(request) + "#" + index).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static byte[] seedOf(RouteRequest request) {
        try {
            return MessageDigest.getInstance("SHA-256")
                .digest(canonicalForm(request).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RouteProviderException("SHA-256 is unavailable in this JVM", e);
        }
    }

    private static String canonicalForm(RouteRequest request) {
        StringBuilder canonical = new StringBuilder();
        for (Coordinate stop : request.orderedStops()) {
            canonical.append(String.format(Locale.ROOT, "%.6f,%.6f;", stop.latitude(), stop.longitude()));
        }
        canonical.append("|")
            .append(request.vehicleLimits().heightMillimetres()).append(",")
            .append(request.vehicleLimits().weightKilograms())
            .append("|");
        request.avoidances().stream()
            .map(Enum::name)
            .sorted()
            .forEach(name -> canonical.append(name).append(","));
        return canonical.toString();
    }
}
