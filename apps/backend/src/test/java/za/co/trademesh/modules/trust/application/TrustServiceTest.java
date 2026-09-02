package za.co.trademesh.modules.trust.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.trademesh.modules.business.application.BusinessTrustCatalog;
import za.co.trademesh.modules.evidence.application.BusinessTrustEvidenceCatalog;
import za.co.trademesh.modules.trust.domain.PublicTrustSummary;
import za.co.trademesh.modules.trust.domain.TrustHistoryBand;
import za.co.trademesh.modules.trust.domain.TrustRepository;

class TrustServiceTest {

    private static final UUID BUSINESS_ID = UUID.fromString("23000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-03T10:00:00Z");

    private FakeTrustRepository repository;

    @BeforeEach
    void setUp() {
        repository = new FakeTrustRepository();
    }

    @Test
    void calculatesAPlainVersionedSummaryWithoutInventingARating() {
        TrustService service = service(new BusinessTrustEvidenceCatalog.CompletionStats(3, 2, 42));

        PublicTrustSummary summary = service.recalculate(BUSINESS_ID);

        assertThat(summary.registryVerified()).isTrue();
        assertThat(summary.completedTransactionCount()).isEqualTo(3);
        assertThat(summary.successfulDeliveryCount()).isEqualTo(2);
        assertThat(summary.deliverySuccessRate()).isEqualByComparingTo("0.6667");
        assertThat(summary.historyBand()).isEqualTo(TrustHistoryBand.LIMITED_COMPLETED_HISTORY);
        assertThat(summary.averageRating()).isNull();
        assertThat(summary.ratingCount()).isZero();
        assertThat(summary.calculationVersion()).isEqualTo("public-trust/v1");
        assertThat(summary.sourceEvidenceThroughSequence()).isEqualTo(42);
    }

    @Test
    void reportsNoRateForNoHistoryAndRecalculationIsIdempotent() {
        TrustService service = service(new BusinessTrustEvidenceCatalog.CompletionStats(0, 0, 0));

        PublicTrustSummary first = service.recalculate(BUSINESS_ID);
        PublicTrustSummary replayed = service.recalculate(BUSINESS_ID);

        assertThat(replayed).isEqualTo(first);
        assertThat(replayed.deliverySuccessRate()).isNull();
        assertThat(replayed.historyBand()).isEqualTo(TrustHistoryBand.NO_COMPLETED_HISTORY);
    }

    @Test
    void refusesToCreateTrustForAnUnknownBusiness() {
        TrustService service = new TrustService(
                repository,
                ignored -> Optional.empty(),
                ignored -> new BusinessTrustEvidenceCatalog.CompletionStats(0, 0, 0),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.recalculate(BUSINESS_ID))
                .isInstanceOf(TrustException.class)
                .extracting(error -> ((TrustException) error).code())
                .isEqualTo("TRUST_BUSINESS_NOT_FOUND");
    }

    private TrustService service(BusinessTrustEvidenceCatalog.CompletionStats stats) {
        BusinessTrustCatalog businesses = businessId -> BUSINESS_ID.equals(businessId)
                ? Optional.of(new BusinessTrustCatalog.BusinessTrustFacts(BUSINESS_ID, true, false))
                : Optional.empty();
        return new TrustService(repository, businesses, ignored -> stats, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static final class FakeTrustRepository implements TrustRepository {
        private final Map<UUID, PublicTrustSummary> summaries = new HashMap<>();

        @Override
        public Optional<PublicTrustSummary> find(UUID businessId) {
            return Optional.ofNullable(summaries.get(businessId));
        }

        @Override
        public void save(PublicTrustSummary summary) {
            summaries.put(summary.businessId(), summary);
        }
    }
}
