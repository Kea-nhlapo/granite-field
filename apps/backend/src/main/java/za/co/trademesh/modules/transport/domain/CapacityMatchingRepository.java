package za.co.trademesh.modules.transport.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CapacityMatchingRepository {

    boolean saveSearch(CapacityMatchSearch search);

    Optional<CapacityMatchSearch> findSearch(UUID businessId, UUID searchId);

    Optional<CapacityMatchSearch> findSearchForUpdate(UUID businessId, UUID searchId);

    Optional<CapacityMatchSearch> findSearchForUpdateById(UUID searchId);

    Optional<CapacityMatchSearch> findSearchByRequestId(UUID businessId, UUID requestId);

    Optional<CapacityMatchCandidate> findCandidate(UUID searchId, UUID offerId);

    boolean saveReservation(CapacityReservation reservation);

    Optional<CapacityReservation> findReservation(UUID searchId);

    Optional<CapacityReservation> findReservationForUpdate(UUID reservationId);

    boolean markSearchStatus(UUID searchId, CapacityMatchStatus expected, CapacityMatchStatus updated);

    boolean markReservationTerminal(UUID reservationId, CapacityReservationStatus status, Instant releasedAt);

    List<UUID> findExpiredActiveReservationIds(Instant now, int limit);
}
