package za.co.trademesh.modules.routing.port;

import za.co.trademesh.modules.routing.domain.RouteCandidateSet;
import za.co.trademesh.modules.routing.domain.RouteRequest;

/**
 * The domain's view of a routing engine. Implementations translate to and from
 * whatever a provider actually speaks; raw provider responses never cross this
 * boundary, so swapping map vendors does not reach the domain.
 */
@FunctionalInterface
public interface RouteProvider {

    /**
     * @throws za.co.trademesh.modules.routing.domain.RouteProviderException
     *         if candidates cannot be produced
     */
    RouteCandidateSet findCandidates(RouteRequest request);
}
