package za.co.trademesh.modules.trust.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.access.application.UserBusinessCatalog;
import za.co.trademesh.modules.business.application.BusinessTrustCatalog;
import za.co.trademesh.modules.evidence.application.BusinessTrustEvidenceCatalog;
import za.co.trademesh.modules.evidence.application.BusinessTrustEvidenceCatalog.ScoreEvent;
import za.co.trademesh.modules.trust.domain.TrustScoreRepository;
import za.co.trademesh.modules.trust.domain.TrustScoreSnapshot;

@Service
public class TrustScoreService {

    private static final BigDecimal BASE_SCORE = new BigDecimal("50.00");
    private static final BigDecimal MINIMUM_SCORE = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal MAXIMUM_SCORE = new BigDecimal("100.00");

    private final TrustScoreRepository scores;
    private final BusinessTrustCatalog businesses;
    private final BusinessTrustEvidenceCatalog evidence;
    private final UserBusinessCatalog userBusinesses;
    private final TrustScoreProperties properties;
    private final Clock clock;

    public TrustScoreService(
            TrustScoreRepository scores,
            BusinessTrustCatalog businesses,
            BusinessTrustEvidenceCatalog evidence,
            UserBusinessCatalog userBusinesses,
            TrustScoreProperties properties,
            Clock clock) {
        this.scores = scores;
        this.businesses = businesses;
        this.evidence = evidence;
        this.userBusinesses = userBusinesses;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TrustScoreSnapshot getForUser(UUID userId) {
        UUID businessId =
                userBusinesses.findPrimaryBusinessId(required(userId)).orElseThrow(TrustException::businessNotFound);
        return scores.find(businessId).orElseThrow(TrustException::scoreNotFound);
    }

    @Transactional(readOnly = true)
    public TrustScoreSnapshot getForBusiness(UUID businessId) {
        return scores.find(required(businessId)).orElseThrow(TrustException::scoreNotFound);
    }

    /** Rebuilds the quick score from evidence already appended in the publishing transaction. */
    @Transactional
    public TrustScoreSnapshot computeProvisional(UUID businessId) {
        UUID owner = required(businessId);
        var business = businesses.find(owner).orElseThrow(TrustException::businessNotFound);
        var history = evidence.scoreHistory(owner);
        Instant now = now();
        BigDecimal provisional = rawScore(business, history, now, false);
        TrustScoreSnapshot current = scores.find(owner).orElse(null);
        TrustScoreSnapshot updated = new TrustScoreSnapshot(
                owner,
                provisional,
                current == null ? BASE_SCORE : current.verifiedScore(),
                properties.verificationScheduleMode(),
                properties.calculationVersion(),
                history.sourceThroughSequence(),
                now,
                current == null ? now : current.verifiedCalculatedAt(),
                current == null ? now.plus(properties.verifiedInterval()) : current.nextVerificationAt());
        scores.save(updated);
        return updated;
    }

    /** Applies time decay and caps movement so one new fact cannot abruptly rewrite verified history. */
    @Transactional
    public TrustScoreSnapshot computeVerified(UUID businessId) {
        UUID owner = required(businessId);
        var business = businesses.find(owner).orElseThrow(TrustException::businessNotFound);
        TrustScoreSnapshot current = scores.find(owner).orElseGet(() -> computeProvisional(owner));
        var history = evidence.scoreHistory(owner);
        Instant now = now();
        BigDecimal raw = rawScore(business, history, now, true);
        BigDecimal verified = capMovement(current.verifiedScore(), raw, properties.maximumVerifiedMovement());
        TrustScoreSnapshot updated = new TrustScoreSnapshot(
                owner,
                rawScore(business, history, now, false),
                verified,
                properties.verificationScheduleMode(),
                properties.calculationVersion(),
                history.sourceThroughSequence(),
                now,
                now,
                now.plus(properties.verifiedInterval()));
        scores.save(updated);
        return updated;
    }

    @Transactional
    public int computeDueVerified() {
        Instant now = now();
        var due = scores.findDueBusinessIds(now, properties.verificationBatchSize());
        due.forEach(this::computeVerified);
        return due.size();
    }

    private BigDecimal rawScore(
            BusinessTrustCatalog.BusinessTrustFacts business,
            BusinessTrustEvidenceCatalog.ScoreHistory history,
            Instant calculatedAt,
            boolean decay) {
        double total = BASE_SCORE.doubleValue();
        if (business.registryVerified()) {
            total += 15;
        }
        if (business.identityVerified()) {
            total += 5;
        }
        for (ScoreEvent event : history.events()) {
            double contribution = contribution(event);
            if (decay) {
                contribution *= decay(event.occurredAt(), calculatedAt, properties.evidenceHalfLife());
                contribution = Math.max(
                        -properties.maximumVerifiedMovement().doubleValue(),
                        Math.min(properties.maximumVerifiedMovement().doubleValue(), contribution));
            }
            total += contribution;
        }
        return bounded(total);
    }

    private static double contribution(ScoreEvent event) {
        return switch (event.type()) {
            case "business.profile-confirmed" -> 0;
            case "HANDOVER_FINALIZED" -> "COMPLETED".equals(event.outcome()) ? 10 : -15;
            case "ESCROW_LOCKED" -> 4;
            case "ESCROW_RELEASED" -> 6;
            case "ESCROW_LOCK_FAILED", "ESCROW_RELEASE_FAILED" -> -8;
            case "RISK_INDICATOR_OPENED" -> -6;
            default -> 0;
        };
    }

    private static double decay(Instant occurredAt, Instant calculatedAt, Duration halfLife) {
        long ageSeconds = Math.max(0, Duration.between(occurredAt, calculatedAt).toSeconds());
        return Math.pow(0.5, (double) ageSeconds / halfLife.toSeconds());
    }

    private static BigDecimal capMovement(BigDecimal previous, BigDecimal requested, BigDecimal maximumMovement) {
        BigDecimal lower = previous.subtract(maximumMovement);
        BigDecimal upper = previous.add(maximumMovement);
        return requested
                .max(lower)
                .min(upper)
                .max(MINIMUM_SCORE)
                .min(MAXIMUM_SCORE)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal bounded(double value) {
        return BigDecimal.valueOf(Math.max(0, Math.min(100, value))).setScale(2, RoundingMode.HALF_UP);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static UUID required(UUID value) {
        if (value == null) {
            throw TrustException.businessNotFound();
        }
        return value;
    }
}
