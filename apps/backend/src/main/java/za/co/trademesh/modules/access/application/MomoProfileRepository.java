package za.co.trademesh.modules.access.application;

import java.time.Instant;
import java.util.UUID;
import za.co.trademesh.modules.payment.application.MomoClient;

public interface MomoProfileRepository {

    void save(UUID userId, String phoneNumber, MomoClient.UserInfo userInfo, Instant verifiedAt);
}
