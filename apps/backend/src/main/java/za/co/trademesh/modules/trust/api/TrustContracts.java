package za.co.trademesh.modules.trust.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.trademesh.modules.trust.application.PremiumEstimateService;
import za.co.trademesh.modules.trust.domain.PublicTrustSummary;
import za.co.trademesh.modules.trust.domain.TrustHistoryBand;
import za.co.trademesh.modules.trust.domain.TrustScoreSnapshot;

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

    record ScoreResponse(
            UUID userId,
            UUID businessId,
            BigDecimal provisionalScore,
            BigDecimal verifiedScore,
            String verifiedScheduleMode,
            String calculationVersion,
            Instant provisionalCalculatedAt,
            Instant verifiedCalculatedAt,
            Instant nextVerificationAt) {

        static ScoreResponse from(UUID userId, TrustScoreSnapshot score) {
            return new ScoreResponse(
                    userId,
                    score.businessId(),
                    score.provisionalScore(),
                    score.verifiedScore(),
                    score.verificationScheduleMode(),
                    score.calculationVersion(),
                    score.provisionalCalculatedAt(),
                    score.verifiedCalculatedAt(),
                    score.nextVerificationAt());
        }
    }

    record PremiumEstimateResponse(
            UUID shipmentId,
            UUID businessId,
            BigDecimal cargoValue,
            String currency,
            BigDecimal verifiedTrustScore,
            BigDecimal platformRate,
            BigDecimal platformPremium,
            BigDecimal genericInsurerRate,
            BigDecimal genericInsurerPremium,
            BigDecimal estimatedSaving,
            String status,
            String calculationVersion,
            Instant estimatedAt) {

        static PremiumEstimateResponse from(PremiumEstimateService.PremiumEstimate estimate) {
            return new PremiumEstimateResponse(
                    estimate.shipmentId(),
                    estimate.businessId(),
                    estimate.cargoValue(),
                    estimate.currency(),
                    estimate.verifiedTrustScore(),
                    estimate.platformRate(),
                    estimate.platformPremium(),
                    estimate.genericInsurerRate(),
                    estimate.genericInsurerPremium(),
                    estimate.estimatedSaving(),
                    estimate.status(),
                    estimate.calculationVersion(),
                    estimate.estimatedAt());
        }
    }
}
