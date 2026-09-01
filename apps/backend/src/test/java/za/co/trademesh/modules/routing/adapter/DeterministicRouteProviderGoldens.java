package za.co.trademesh.modules.routing.adapter;

import java.util.List;

/**
 * Golden values for Johannesburg to Pretoria, no waypoints, no avoidances,
 * 4200mm / 26000kg limits — captured once from the implementation.
 *
 * <p>These pin DETERMINISM, not correctness: they prove the adapter returns the
 * same candidates in a fresh JVM, which comparing two calls in one process
 * cannot. Whether 54.6km is a sensible Johannesburg-Pretoria route is asserted
 * separately, against the great-circle distance.
 *
 * <p>If a change to the adapter breaks these, that is the point — it means
 * issue #17's scoring fixtures have shifted underneath it. Update deliberately.
 */
final class DeterministicRouteProviderGoldens {

    static final List<String> CANDIDATE_IDS = List.of(
        "023f2fa4-83f5-339d-8185-d7d336f0666f",
        "d16ede16-6277-36c3-9bf1-70ef87803fc9",
        "34e2a8d8-3562-3e92-8131-13d22831a67a");

    static final List<Long> DISTANCES_METRES = List.of(54_660L, 58_764L, 62_557L);

    private DeterministicRouteProviderGoldens() {
    }
}
