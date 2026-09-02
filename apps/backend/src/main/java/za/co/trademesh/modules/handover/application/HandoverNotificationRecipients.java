package za.co.trademesh.modules.handover.application;

import java.util.Optional;
import java.util.UUID;

/** Handover participants exposed without QR or confirmation evidence. */
public interface HandoverNotificationRecipients {

    Optional<Participants> find(UUID challengeId);

    record Participants(UUID initiatorUserId, UUID counterpartyUserId) {}
}
