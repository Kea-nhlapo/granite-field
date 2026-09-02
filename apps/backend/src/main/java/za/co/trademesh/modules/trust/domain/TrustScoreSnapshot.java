package za.co.trademesh.modules.trust.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TrustScoreSnapshot(
        UUID businessId,
        BigDecimal provisionalScore,
        BigDecimal verifiedScore,
        String verificationScheduleMode,
        String calculationVersion,
        long sourceEvidenceThroughSequence,
        Instant provisionalCalculatedAt,
        Instant verifiedCalculatedAt,
        Instant nextVerificationAt) {}
