package za.co.trademesh.modules.handover.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface HandoverRepository {

    int expireActive(UUID shipmentId, HandoverType type, UUID deliveryOrderId, Instant now);

    Optional<HandoverChallenge> findActive(UUID shipmentId, HandoverType type, UUID deliveryOrderId);

    boolean save(HandoverChallenge challenge);

    Optional<HandoverChallenge> findOwned(UUID businessId, UUID shipmentId, UUID challengeId);

    Optional<HandoverChallenge> findByNonceHashForUpdate(String nonceHash);

    Optional<HandoverConfirmation> findConfirmationByCommandId(UUID commandId);

    boolean saveConfirmation(HandoverConfirmation confirmation);

    boolean changeState(UUID challengeId, HandoverState expected, HandoverState target, Instant completedAt);

    Set<UUID> findFinalizedDeliveryOrderIds(UUID shipmentId);

    void saveAttempt(HandoverAttempt attempt);
}
