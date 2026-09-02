package za.co.trademesh.modules.aggregation.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.aggregation.domain.DemandGroupSuggestionRepository;
import za.co.trademesh.modules.aggregation.domain.DemandGroupSuggestionStatus;
import za.co.trademesh.modules.aggregation.domain.DemandOrderEvaluation;
import za.co.trademesh.modules.procurement.application.AggregationOrderCatalog;

@Service
class ConsolidatedDemandService implements ConsolidatedDemandCatalog {

    private final DemandGroupSuggestionRepository suggestions;
    private final AggregationOrderCatalog orders;

    ConsolidatedDemandService(DemandGroupSuggestionRepository suggestions, AggregationOrderCatalog orders) {
        this.suggestions = suggestions;
        this.orders = orders;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ConsolidatedDemand> findActive(UUID requestedByBusinessId, UUID suggestionId) {
        var suggestion = suggestions.findById(requestedByBusinessId, suggestionId);
        if (suggestion.isEmpty() || suggestion.get().status() != DemandGroupSuggestionStatus.ACTIVE) {
            return Optional.empty();
        }
        List<DeliveryStop> stops = new ArrayList<>();
        for (DemandOrderEvaluation evaluation : suggestion.get().orderEvaluations()) {
            if (!evaluation.included()) {
                continue;
            }
            var order = orders.findConfirmedOrder(evaluation.buyerBusinessId(), evaluation.orderId());
            if (order.isEmpty()) {
                return Optional.empty();
            }
            var value = order.get();
            stops.add(new DeliveryStop(
                    value.orderId(),
                    value.buyerBusinessId(),
                    value.destinationLabel(),
                    value.destinationLatitude(),
                    value.destinationLongitude(),
                    value.deliveryWindowStart(),
                    value.deliveryWindowEnd(),
                    value.cargoItems().stream()
                            .map(item -> new CargoItem(item.productCode(), item.unitOfMeasure()))
                            .toList()));
        }
        stops.sort(Comparator.comparing(DeliveryStop::orderId));
        if (stops.size() < 2) {
            return Optional.empty();
        }
        return Optional.of(new ConsolidatedDemand(
                suggestion.get().id(),
                suggestion.get().requestedByBusinessId(),
                suggestion.get().anchorOrderId(),
                stops));
    }
}
