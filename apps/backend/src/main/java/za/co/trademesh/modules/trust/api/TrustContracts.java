package za.co.trademesh.modules.trust.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.trademesh.modules.trust.domain.PublicTrustSummary;
import za.co.trademesh.modules.trust.domain.TrustHistoryBand;

final class TrustContracts {

    private TrustContracts() {}

    record PublicSummaryResponse(
            UUID businessId,
            List<String> verifiedBadges,
            int completedTransactionCount,
            BigDecimal deliverySuccessRate,
            RatingResponse rating,
            TrustHistoryBand historyBand,
            String calculationVersion,
            Instant calculatedAt) {

        static PublicSummaryResponse from(PublicTrustSummary summary) {
            List<String> badges = new java.util.ArrayList<>();
            if (summary.registryVerified()) {
                badges.add("CIPC_VERIFIED");
            }
            if (summary.identityVerified()) {
                badges.add("IDENTITY_VERIFIED");
            }
            return new PublicSummaryResponse(
                    summary.businessId(),
                    List.copyOf(badges),
                    summary.completedTransactionCount(),
                    summary.deliverySuccessRate(),
                    RatingResponse.from(summary),
                    summary.historyBand(),
                    summary.calculationVersion(),
                    summary.calculatedAt());
        }
    }

    record RatingResponse(BigDecimal average, int outOf, int ratingCount, String status) {
        static RatingResponse from(PublicTrustSummary summary) {
            return new RatingResponse(
                    summary.averageRating(),
                    5,
                    summary.ratingCount(),
                    summary.ratingCount() == 0 ? "NOT_YET_RATED" : "RATED");
        }
    }

    record InternalCalculationResponse(
            PublicSummaryResponse publicSummary, int successfulDeliveryCount, long sourceEvidenceThroughSequence) {

        static InternalCalculationResponse from(PublicTrustSummary summary) {
            return new InternalCalculationResponse(
                    PublicSummaryResponse.from(summary),
                    summary.successfulDeliveryCount(),
                    summary.sourceEvidenceThroughSequence());
        }
    }
}
