package za.co.trademesh.modules.trust.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PublicTrustSummary(
        UUID businessId,
        boolean registryVerified,
        boolean identityVerified,
        int completedTransactionCount,
        int successfulDeliveryCount,
        BigDecimal deliverySuccessRate,
        BigDecimal averageRating,
        int ratingCount,
        TrustHistoryBand historyBand,
        String calculationVersion,
        long sourceEvidenceThroughSequence,
        Instant calculatedAt) {}
