package za.co.trademesh.modules.business.domain;

import java.time.Instant;
import java.util.UUID;

public record BusinessProfile(
        UUID id,
        RegistrationNumber registrationNumber,
        String legalName,
        String tradingName,
        String registeredAddress,
        BusinessVerificationStatus verificationStatus,
        BusinessLifecycleStatus lifecycleStatus,
        UUID confirmedByUserId,
        Instant createdAt) {}
