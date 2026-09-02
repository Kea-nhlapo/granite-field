package za.co.trademesh.modules.evidence.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import za.co.trademesh.modules.evidence.domain.EvidenceChainLink;
import za.co.trademesh.modules.evidence.domain.EvidenceDraft;
import za.co.trademesh.modules.evidence.domain.EvidenceFileReference;
import za.co.trademesh.modules.evidence.domain.EvidenceRecord;
import za.co.trademesh.modules.evidence.domain.EvidenceRepository;

@Repository
class JdbcEvidenceRepository implements EvidenceRepository {

    private static final String COLUMNS = """
        ledger_sequence, id, event_id, evidence_type, subject_type, subject_id, shipment_id,
        occurred_at, actor, source, correlation_id, schema_version, correction_of_id,
        metadata, payload_checksum, previous_chain_hash, chain_hash, recorded_at
        """;
    private static final TypeReference<Map<String, String>> METADATA_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    JdbcEvidenceRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public long nextSequence() {
        Long value = jdbcTemplate.queryForObject("SELECT nextval('evidence_ledger_sequence')", Long.class);
        if (value == null) {
            throw new IllegalStateException("The evidence sequence did not return a value");
        }
        return value;
    }

    @Override
    public AppendResult append(
            EvidenceDraft draft, String payloadChecksum, Optional<EvidenceChainLink> requestedChainLink) {
        EvidenceChainLink chainLink = requestedChainLink.orElse(null);
        int inserted = jdbcTemplate.update(
                """
            INSERT INTO evidence_record (
                ledger_sequence, id, event_id, evidence_type, subject_type, subject_id, shipment_id,
                occurred_at, actor, source, correlation_id, schema_version, correction_of_id,
                metadata, payload_checksum, previous_chain_hash, chain_hash, recorded_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
            ON CONFLICT (event_id) DO NOTHING
            """,
                draft.ledgerSequence(),
                draft.id(),
                draft.eventId(),
                draft.type(),
                draft.subjectType(),
                draft.subjectId(),
                draft.shipmentId(),
                time(draft.occurredAt()),
                draft.actor(),
                draft.source(),
                draft.correlationId(),
                draft.schemaVersion(),
                draft.correctionOfId(),
                objectMapper.writeValueAsString(draft.metadata()),
                payloadChecksum,
                chainLink == null ? null : chainLink.previousHash(),
                chainLink == null ? null : chainLink.chainHash(),
                time(draft.recordedAt()));

        if (inserted == 1) {
            for (EvidenceFileReference file : draft.files()) {
                jdbcTemplate.update("""
                    INSERT INTO evidence_file_reference (evidence_id, file_id, sha256)
                    VALUES (?, ?, ?)
                    """, draft.id(), file.fileId(), file.sha256());
            }
        }
        EvidenceRecord record = findByEventId(draft.eventId())
                .orElseThrow(() -> new IllegalStateException("The appended evidence could not be reloaded"));
        return new AppendResult(record, inserted == 1);
    }

    @Override
    public Optional<EvidenceRecord> findById(UUID evidenceId) {
        return one("SELECT " + COLUMNS + " FROM evidence_record WHERE id = ?", evidenceId);
    }

    @Override
    public Optional<EvidenceRecord> findByEventId(UUID eventId) {
        return one("SELECT " + COLUMNS + " FROM evidence_record WHERE event_id = ?", eventId);
    }

    @Override
    public List<EvidenceRecord> findByShipmentId(UUID shipmentId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS
                        + " FROM evidence_record WHERE shipment_id = ? ORDER BY occurred_at, ledger_sequence",
                this::mapRecord,
                shipmentId);
    }

    private Optional<EvidenceRecord> one(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, this::mapRecord, parameters).stream().findFirst();
    }

    private EvidenceRecord mapRecord(ResultSet resultSet, int rowNumber) throws SQLException {
        UUID evidenceId = resultSet.getObject("id", UUID.class);
        return new EvidenceRecord(
                resultSet.getLong("ledger_sequence"),
                evidenceId,
                resultSet.getObject("event_id", UUID.class),
                resultSet.getString("evidence_type"),
                resultSet.getString("subject_type"),
                resultSet.getObject("subject_id", UUID.class),
                resultSet.getObject("shipment_id", UUID.class),
                instant(resultSet, "occurred_at"),
                resultSet.getString("actor"),
                resultSet.getString("source"),
                resultSet.getObject("correlation_id", UUID.class),
                resultSet.getInt("schema_version"),
                resultSet.getObject("correction_of_id", UUID.class),
                objectMapper.readValue(resultSet.getString("metadata"), METADATA_TYPE),
                files(evidenceId),
                resultSet.getString("payload_checksum"),
                resultSet.getString("previous_chain_hash"),
                resultSet.getString("chain_hash"),
                instant(resultSet, "recorded_at"));
    }

    private List<EvidenceFileReference> files(UUID evidenceId) {
        return jdbcTemplate.query(
                """
            SELECT file_id, sha256
              FROM evidence_file_reference
             WHERE evidence_id = ?
             ORDER BY file_id
            """,
                (resultSet, rowNumber) -> new EvidenceFileReference(
                        resultSet.getObject("file_id", UUID.class), resultSet.getString("sha256")),
                evidenceId);
    }

    private static OffsetDateTime time(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }
}
