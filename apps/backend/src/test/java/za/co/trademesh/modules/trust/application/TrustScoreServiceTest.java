package za.co.trademesh.modules.trust.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.trademesh.modules.business.application.BusinessTrustCatalog;
import za.co.trademesh.modules.evidence.application.BusinessTrustEvidenceCatalog;
import za.co.trademesh.modules.trust.domain.TrustScoreRepository;
import za.co.trademesh.modules.trust.domain.TrustScoreSnapshot;

class TrustScoreServiceTest {

    private static final UUID BUSINESS_ID = UUID.fromString("77000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("77000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-09-03T19:00:00Z");

    @Test
    void updatesProvisionalImmediatelyAndCapsVerifiedMovement() {
        FakeScoreRepository repository = new FakeScoreRepository();
        var history = new BusinessTrustEvidenceCatalog.ScoreHistory(
                List.of(new BusinessTrustEvidenceCatalog.ScoreEvent(
                        "HANDOVER_FINALIZED", "COMPLETED", NOW.minusSeconds(60), 9)),
                9);
        TrustScoreService service = service(repository, history);

        TrustScoreSnapshot provisional = service.computeProvisional(BUSINESS_ID);
        TrustScoreSnapshot verified = service.computeVerified(BUSINESS_ID);

        assertThat(provisional.provisionalScore()).isEqualByComparingTo("75.00");
        assertThat(provisional.verifiedScore()).isEqualByComparingTo("50.00");
        assertThat(verified.verifiedScore()).isEqualByComparingTo("55.00");
        assertThat(verified.verifiedScore().subtract(provisional.verifiedScore()))
                .isEqualByComparingTo("5.00");
        assertThat(verified.verificationScheduleMode()).isEqualTo("COMPRESSED_DEMO");
    }

    @Test
    void resolvesThePrimaryBusinessForTheUserWithoutReturningEvidenceDetails() {
        FakeScoreRepository repository = new FakeScoreRepository();
        TrustScoreService service = service(repository, new BusinessTrustEvidenceCatalog.ScoreHistory(List.of(), 0));
        TrustScoreSnapshot calculated = service.computeProvisional(BUSINESS_ID);

        assertThat(service.getForUser(USER_ID)).isEqualTo(calculated);
    }

    private static TrustScoreService service(
            FakeScoreRepository repository, BusinessTrustEvidenceCatalog.ScoreHistory history) {
        BusinessTrustCatalog businesses =
                businessId -> Optional.of(new BusinessTrustCatalog.BusinessTrustFacts(BUSINESS_ID, true, false));
        BusinessTrustEvidenceCatalog evidence = new BusinessTrustEvidenceCatalog() {
            @Override
            public CompletionStats completionStats(UUID businessId) {
                return new CompletionStats(0, 0, history.sourceThroughSequence());
            }

            @Override
            public ScoreHistory scoreHistory(UUID businessId) {
                return history;
            }
        };
        var properties = new TrustScoreProperties(
                Duration.ofSeconds(30),
                Duration.ofDays(180),
                new BigDecimal("5.00"),
                100,
                "COMPRESSED_DEMO",
                "trust-score/v1");
        return new TrustScoreService(
                repository,
                businesses,
                evidence,
                userId -> USER_ID.equals(userId) ? Optional.of(BUSINESS_ID) : Optional.empty(),
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static final class FakeScoreRepository implements TrustScoreRepository {
        private final Map<UUID, TrustScoreSnapshot> snapshots = new HashMap<>();

        @Override
        public Optional<TrustScoreSnapshot> find(UUID businessId) {
            return Optional.ofNullable(snapshots.get(businessId));
        }

        @Override
        public void save(TrustScoreSnapshot snapshot) {
            snapshots.put(snapshot.businessId(), snapshot);
        }

        @Override
        public List<UUID> findDueBusinessIds(Instant dueAt, int limit) {
            return new ArrayList<>();
        }
    }
}
