package za.co.trademesh.modules.routing.domain;

import java.util.Optional;
import java.util.UUID;

public interface RouteCalculationRepository {

    boolean save(RouteCalculation calculation);

    Optional<RouteCalculation> findById(UUID businessId, UUID calculationId);

    Optional<RouteCalculation> findByRequestId(UUID businessId, UUID requestId);
}
