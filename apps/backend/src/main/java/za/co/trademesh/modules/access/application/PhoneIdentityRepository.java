package za.co.trademesh.modules.access.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PhoneIdentityRepository {

    Optional<UUID> findUserId(String phoneNumber);

    void save(String phoneNumber, UUID userId, VerificationMethod method, Instant verifiedAt);

    enum VerificationMethod {
        TWILIO_OTP,
        MOMO_CONSENT
    }
}
