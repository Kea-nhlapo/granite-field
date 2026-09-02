package za.co.trademesh.modules.routing.domain;

import java.util.Optional;
import java.util.UUID;

public interface RouteAssessmentRepository {

    boolean save(RouteAssessment assessment);

    Optional<RouteAssessment> findById(UUID businessId, UUID assessmentId);

    Optional<RouteAssessment> findByRequestId(UUID businessId, UUID requestId);
}
