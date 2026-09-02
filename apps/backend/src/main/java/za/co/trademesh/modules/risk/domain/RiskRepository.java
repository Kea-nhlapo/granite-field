package za.co.trademesh.modules.risk.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RiskRepository {

    RiskIndicator upsertActive(RiskIndicator proposal);

    Optional<RiskIndicator> findById(UUID indicatorId);

    Optional<RiskIndicator> findByIdForUpdate(UUID indicatorId);

    List<RiskIndicator> findByShipment(UUID shipmentId);

    Optional<RiskIndicatorTransition> findTransitionByCommandId(UUID commandId);

    boolean transition(
            UUID indicatorId, RiskIndicatorState expectedState, RiskIndicatorTransition transition, Instant updatedAt);
}
