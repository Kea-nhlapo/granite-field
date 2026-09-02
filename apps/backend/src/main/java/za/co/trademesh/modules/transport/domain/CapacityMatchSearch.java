package za.co.trademesh.modules.transport.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CapacityMatchSearch(
        UUID id,
        UUID requestedByBusinessId,
        UUID clientRequestId,
        UUID demandGroupSuggestionId,
        String inputFingerprint,
        String algorithmVersion,
        Capacity requiredCapacity,
        List<CargoTrait> cargoTraits,
        Instant deliveryWindowStart,
        Instant deliveryWindowEnd,
        int orderCount,
        CapacityMatchStatus status,
        List<CapacityMatchCandidate> candidates,
        UUID createdByUserId,
        Instant createdAt) {

    public CapacityMatchSearch {
        cargoTraits = List.copyOf(cargoTraits);
        candidates = List.copyOf(candidates);
    }
}
