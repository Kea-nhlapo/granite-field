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
        // Built once and threaded through. It was previously rebuilt for every
        // candidate, re-formatting every coordinate each time, on a path #17
        // calls repeatedly while scoring.
        String canonical = canonicalForm(request);
        byte[] seed = seedOf(canonical);

        List<Coordinate> stops = request.orderedStops();
        int legs = stops.size() - 1;
        long directDistance = GreatCircle.metresAlong(stops);
        boolean avoidsTolls = request.avoidances().contains(Avoidance.TOLLS);

        int candidateCount = MINIMUM_CANDIDATES + Math.floorMod(seed[0], 2);
        List<CandidateRoute> candidates = new ArrayList<>(candidateCount);
        for (int index = 0; index < candidateCount; index++) {
            candidates.add(
                candidate(canonical, seed, index, stops, legs, directDistance, avoidsTolls));
        }
        return RouteCandidateSet.of(request, List.copyOf(candidates));
    }

    private CandidateRoute candidate(
        String canonical,
        byte[] seed,
        int index,
        List<Coordinate> stops,
        int legs,
        long directDistance,
        boolean avoidsTolls) {

        double detourFactor = 1.05 + (index * 0.07) + (Math.floorMod(seed[index + 1], 16) / 1000.0);
        // At least one metre per leg, so the per-leg split below can never
        // produce a zero-or-negative segment.
        long distanceMetres = Math.max(legs, Math.round(directDistance * detourFactor));
        Duration duration = Duration.ofSeconds(
            Math.max(legs, Math.round(distanceMetres / AVERAGE_SPEED_METRES_PER_SECOND)));

        Optional<Money> toll = avoidsTolls ? Optional.empty() : Optional.of(tollFor(distanceMetres));

        return new CandidateRoute(
            candidateId(canonical, index),
            segments(stops, legs, index, distanceMetres, duration),
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

    /**
     * Splits the route across its legs so the parts SUM TO THE WHOLE. Integer
     * division alone loses the remainder, which left segment totals disagreeing
     * with the candidate's own distance by a per-route amount — and #17 has every
     * reason to sum segments.
     */
    private List<RouteSegment> segments(
        List<Coordinate> stops, int legs, int index, long distanceMetres, Duration duration) {

        List<RouteSegment> segments = new ArrayList<>(legs);
        long distanceRemaining = distanceMetres;
        Duration durationRemaining = duration;

        for (int leg = 0; leg < legs; leg++) {
            boolean lastLeg = leg == legs - 1;
            long legDistance = lastLeg ? distanceRemaining : distanceMetres / legs;
            Duration legDuration = lastLeg ? durationRemaining : duration.dividedBy(legs);

            Coordinate from = stops.get(leg);
            Coordinate to = stops.get(leg + 1);
            segments.add(new RouteSegment(
                List.of(from, wobbledMidpoint(from, to, index), to), legDistance, legDuration));

            distanceRemaining -= legDistance;
            durationRemaining = durationRemaining.minus(legDuration);
        }
        return segments;
    }

    /**
     * A midpoint nudged aside, differently per candidate, so candidates are
     * distinguishable geometry rather than the same straight line repeated. The
     * clamp only bites at a pole or the antimeridian, neither of which appears on
     * a SADC corridor.
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

    private String candidateId(String canonical, int index) {
        return UUID.nameUUIDFromBytes(
            (canonical + "#" + index).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static byte[] seedOf(String canonical) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
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
        // Sorted, so the seed cannot depend on a set's iteration order.
        request.avoidances().stream()
            .map(Enum::name)
            .sorted()
            .forEach(name -> canonical.append(name).append(","));
        return canonical.toString();
    }
}
