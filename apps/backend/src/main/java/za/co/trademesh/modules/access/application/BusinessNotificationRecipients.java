package za.co.trademesh.modules.access.application;

import java.util.List;
import java.util.UUID;

/** Active business members exposed without leaking membership persistence. */
public interface BusinessNotificationRecipients {

    List<UUID> findActiveUserIds(UUID businessId);
}
