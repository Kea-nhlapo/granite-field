package za.co.trademesh.modules.evidence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.trademesh.modules.shipment.events.ShipmentEvent;
import za.co.trademesh.modules.telemetry.domain.TelemetryNetworkStatus;
import za.co.trademesh.modules.telemetry.events.TelemetryEvent;
import za.co.trademesh.shared.events.DomainEvents;
import za.co.trademesh.support.PostgresIntegrationTest;

class EvidenceLedgerIntegrationTest extends PostgresIntegrationTest {

    private static final String FILE_CHECKSUM = "a".repeat(64);

    @Autowired
    private DomainEvents domainEvents;

    @Autowired
    private EvidenceLedger ledger;

    @Autowired
    private ShipmentEvidenceCatalog catalog;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void capturesSafeShipmentFactsSupportsCorrectionsAndRejectsMutation() {
        UUID shipmentId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        publish(
                new ShipmentEvent.ShipmentCreated(
                        shipmentId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                actorId);

        ShipmentEvidenceCatalog.ShipmentEvidencePackage initial = catalog.packageFor(shipmentId);
        assertThat(initial.entries()).hasSize(1);
        ShipmentEvidenceCatalog.Entry original = initial.entries().getFirst();
        assertThat(original.type()).isEqualTo("SHIPMENT_CREATED");
        assertThat(original.subjectType()).isEqualTo("SHIPMENT");
        assertThat(original.subjectId()).isEqualTo(shipmentId);
        assertThat(original.actor()).isEqualTo(actorId.toString());
        assertThat(original.integrity()).isEqualTo(ShipmentEvidenceCatalog.Integrity.VERIFIED);
        assertThat(original.metadata())
                .containsOnlyKeys(
                        "requestedByBusinessId",
                        "demandGroupSuggestionId",
                        "capacityReservationId",
                        "routeCandidateId");

        UUID fileId = insertAvailableFile();
        EvidenceCorrection correction = new EvidenceCorrection(
                original.evidenceId(),
                UUID.randomUUID(),
                "SHIPMENT_CREATED_CORRECTED",
                original.occurredAt().plusSeconds(1),
                actorId.toString(),
                "evidence-test",
                UUID.randomUUID(),
                1,
                Map.of("reason", "Corrected supporting document reference"),
                List.of(new EvidenceFile(fileId, FILE_CHECKSUM)));
        var corrected = ledger.correct(correction);

        ShipmentEvidenceCatalog.ShipmentEvidencePackage evidencePackage = catalog.packageFor(shipmentId);
        assertThat(evidencePackage.entries()).hasSize(2);
        assertThat(evidencePackage.entries().get(1).correctionOfId()).isEqualTo(original.evidenceId());
        assertThat(evidencePackage.entries().get(1).files())
                .containsExactly(new ShipmentEvidenceCatalog.FileReference(fileId, FILE_CHECKSUM));
        assertThat(evidencePackage.entries())
                .extracting(ShipmentEvidenceCatalog.Entry::integrity)
                .containsOnly(ShipmentEvidenceCatalog.Integrity.VERIFIED);

        jdbcTemplate.update("DELETE FROM stored_file WHERE id = ?", fileId);
        assertThat(catalog.packageFor(shipmentId).entries().get(1).files())
                .containsExactly(new ShipmentEvidenceCatalog.FileReference(fileId, FILE_CHECKSUM));

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE evidence_record SET source = 'changed' WHERE id = ?", corrected.id()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM evidence_record WHERE id = ?", corrected.id()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "DELETE FROM evidence_file_reference WHERE evidence_id = ?", corrected.id()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void deliberatelyDoesNotStoreEveryRawTelemetryReading() {
        UUID shipmentId = UUID.randomUUID();
        publish(
                new TelemetryEvent.ReadingAccepted(
                        UUID.randomUUID(),
                        shipmentId,
                        UUID.randomUUID(),
                        Instant.now(),
                        Instant.now(),
                        -26.1,
                        28.0,
                        BigDecimal.ZERO,
                        new BigDecimal("200.0"),
                        null,
                        false,
                        new BigDecimal("90.0"),
                        TelemetryNetworkStatus.CONNECTED,
                        -70),
                null);

        assertThat(catalog.packageFor(shipmentId).entries()).isEmpty();
    }

    private void publish(za.co.trademesh.shared.events.DomainEvent event, UUID actorId) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> domainEvents.publish(event, actorId == null ? null : actorId.toString()));
    }

    private UUID insertAvailableFile() {
        UUID userId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String registrationNumber =
                "2099/" + String.format("%06d", Math.floorMod(userId.hashCode(), 1_000_000)) + "/07";
        jdbcTemplate.update(
                "INSERT INTO access_user_account (id, email, password_hash, enabled, created_at) VALUES (?, ?, ?, true, ?)",
                userId,
                "evidence-" + userId + "@example.test",
                "test-password-hash",
                now);
        jdbcTemplate.update("""
            INSERT INTO business_profile (
                id, registration_number, legal_name, registered_address,
                verification_status, lifecycle_status, confirmed_by_user_id, created_at
            ) VALUES (?, ?, 'Evidence Test', 'Johannesburg', 'REGISTRY_VERIFIED', 'ACTIVE', ?, ?)
            """, businessId, registrationNumber, userId, now);
        jdbcTemplate.update(
                """
            INSERT INTO stored_file (
                id, business_id, category, original_filename, object_key, content_type,
                extension, size_bytes, sha256, scan_status, storage_status,
                uploaded_by_user_id, created_at, stored_at
            ) VALUES (?, ?, 'DELIVERY_PROOF', 'proof.pdf', ?, 'application/pdf',
                      'pdf', 10, ?, 'CLEAN', 'AVAILABLE', ?, ?, ?)
            """, fileId, businessId, "objects/evidence/" + fileId + ".pdf", FILE_CHECKSUM, userId, now, now);
        return fileId;
    }
}
