package za.co.trademesh.modules.trust.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.business.application.BusinessTrustCatalog;
import za.co.trademesh.modules.evidence.application.BusinessTrustEvidenceCatalog;
import za.co.trademesh.modules.trust.domain.PublicTrustSummary;
import za.co.trademesh.modules.trust.domain.TrustHistoryBand;
import za.co.trademesh.modules.trust.domain.TrustRepository;

@Service
public class TrustService {

    public static final String CALCULATION_VERSION = "public-trust/v1";
    private static final int ESTABLISHED_HISTORY_MINIMUM = 10;

    private final TrustRepository summaries;
    private final BusinessTrustCatalog businesses;
    private final BusinessTrustEvidenceCatalog evidence;
    private final Clock clock;

    public TrustService(
            TrustRepository summaries,
            BusinessTrustCatalog businesses,
            BusinessTrustEvidenceCatalog evidence,
            Clock clock) {
        this.summaries = summaries;
        this.businesses = businesses;
        this.evidence = evidence;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PublicTrustSummary getPublicSummary(UUID businessId) {
        requireBusiness(businessId);
        return summaries.find(businessId).orElseThrow(TrustException::businessNotFound);
    }

    @Transactional
    public PublicTrustSummary recalculate(UUID businessId) {
        var business = requireBusiness(businessId);
        var completion = evidence.completionStats(businessId);
        PublicTrustSummary calculated = new PublicTrustSummary(
                business.businessId(),
                business.registryVerified(),
                business.identityVerified(),
                completion.completedTransactions(),
                completion.successfulDeliveries(),
                successRate(completion.completedTransactions(), completion.successfulDeliveries()),
                null,
                0,
                historyBand(completion.completedTransactions()),
                CALCULATION_VERSION,
                completion.sourceThroughSequence(),
                clock.instant().truncatedTo(ChronoUnit.MICROS));
        summaries.save(calculated);
        return summaries.find(businessId).orElseThrow();
    }

    private BusinessTrustCatalog.BusinessTrustFacts requireBusiness(UUID businessId) {
        if (businessId == null) {
            throw TrustException.businessNotFound();
        }
        return businesses.find(businessId).orElseThrow(TrustException::businessNotFound);
    }

    private static BigDecimal successRate(int completed, int successful) {
        if (completed == 0) {
            return null;
        }
        return BigDecimal.valueOf(successful).divide(BigDecimal.valueOf(completed), 4, RoundingMode.HALF_UP);
    }

    private static TrustHistoryBand historyBand(int completed) {
        if (completed == 0) {
            return TrustHistoryBand.NO_COMPLETED_HISTORY;
        }
        return completed < ESTABLISHED_HISTORY_MINIMUM
                ? TrustHistoryBand.LIMITED_COMPLETED_HISTORY
                : TrustHistoryBand.ESTABLISHED_COMPLETED_HISTORY;
    }
}
