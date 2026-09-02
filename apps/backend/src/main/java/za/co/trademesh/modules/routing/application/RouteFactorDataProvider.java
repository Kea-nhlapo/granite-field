package za.co.trademesh.modules.routing.application;

import java.math.BigDecimal;
import java.util.Map;
import za.co.trademesh.modules.routing.domain.CandidateRoute;
import za.co.trademesh.modules.routing.domain.RouteCalculation;
import za.co.trademesh.modules.routing.domain.RouteFactor;

public interface RouteFactorDataProvider {

    Map<RouteFactor, Measurement> measure(RouteCalculation calculation, CandidateRoute candidate);

    record Measurement(BigDecimal value, String source) {}
}
