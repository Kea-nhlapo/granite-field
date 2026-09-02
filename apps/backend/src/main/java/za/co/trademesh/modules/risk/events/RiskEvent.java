package za.co.trademesh.modules.risk.events;

import java.util.UUID;
import za.co.trademesh.modules.risk.domain.RiskIndicatorState;
import za.co.trademesh.modules.risk.domain.RiskRule;
import za.co.trademesh.modules.risk.domain.RiskSeverity;
import za.co.trademesh.shared.events.DomainEvent;

public sealed interface RiskEvent extends DomainEvent
        permits RiskEvent.IndicatorOpened, RiskEvent.IndicatorStateChanged {

    @Override
    default int schemaVersion() {
        return 1;
    }

    record IndicatorOpened(UUID indicatorId, UUID shipmentId, RiskRule rule, RiskSeverity severity)
            implements RiskEvent {
        @Override
        public String type() {
            return "RISK_INDICATOR_OPENED";
        }

        public boolean affectsTrustScore() {
            return rule == RiskRule.ROUTE_DEVIATION;
        }
    }

    record IndicatorStateChanged(
            UUID indicatorId,
            UUID shipmentId,
            RiskIndicatorState fromState,
            RiskIndicatorState toState,
            UUID actorUserId)
            implements RiskEvent {
        @Override
        public String type() {
            return "RISK_INDICATOR_STATE_CHANGED";
        }
    }
}
