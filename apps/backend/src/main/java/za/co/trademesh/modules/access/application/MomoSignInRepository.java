package za.co.trademesh.modules.access.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import za.co.trademesh.modules.payment.application.MomoClient;

public interface MomoSignInRepository {

    void save(MomoSignIn signIn);

    Optional<MomoSignIn> findByPollTokenHash(String pollTokenHash);

    void updateStatus(UUID id, MomoClient.ConsentStatus status);

    boolean complete(UUID id, Instant completedAt);
}
