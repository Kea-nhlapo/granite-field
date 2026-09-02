package za.co.trademesh.modules.document.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.trademesh.modules.document.domain.ConfirmedDocumentField;
import za.co.trademesh.modules.document.domain.DocumentConfirmation;
import za.co.trademesh.modules.document.domain.DocumentExtraction;
import za.co.trademesh.modules.document.domain.DocumentRecord;
import za.co.trademesh.modules.document.domain.DocumentRepository;
import za.co.trademesh.modules.document.domain.DocumentState;
import za.co.trademesh.modules.document.domain.DocumentStateTransition;
import za.co.trademesh.modules.document.domain.DocumentType;
import za.co.trademesh.modules.document.domain.DocumentView;
import za.co.trademesh.modules.document.domain.ExtractedDocumentField;

@Repository
class JdbcDocumentRepository implements DocumentRepository {

    private static final String DOCUMENT_COLUMNS = """
        id, business_id, stored_file_id, document_type, state, processing_attempts,
        last_error, created_by_user_id, created_at, updated_at
        """;

    private final JdbcTemplate jdbcTemplate;

    JdbcDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean save(DocumentRecord document, UUID clientRequestId) {
        return jdbcTemplate.update(
                        """
            INSERT INTO document_record (
                id, business_id, stored_file_id, client_request_id, document_type, state,
                processing_attempts, created_by_user_id, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """,
                        document.id(),
                        document.businessId(),
                        document.storedFileId(),
                        clientRequestId,
                        document.type().name(),
                        document.state().name(),
                        document.processingAttempts(),
                        document.createdByUserId(),
                        time(document.createdAt()),
                        time(document.updatedAt()))
                == 1;
    }

    @Override
    public Optional<DocumentRecord> findById(UUID documentId) {
        return one("SELECT " + DOCUMENT_COLUMNS + " FROM document_record WHERE id = ?", documentId);
    }

    @Override
    public Optional<DocumentRecord> findByIdAndBusinessId(UUID documentId, UUID businessId) {
        return one(
                "SELECT " + DOCUMENT_COLUMNS + " FROM document_record WHERE id = ? AND business_id = ?",
                documentId,
                businessId);
    }

    @Override
    public Optional<DocumentRecord> findByBusinessIdAndRequestId(UUID businessId, UUID clientRequestId) {
        return one(
                "SELECT " + DOCUMENT_COLUMNS + " FROM document_record WHERE business_id = ? AND client_request_id = ?",
                businessId,
                clientRequestId);
    }

    @Override
    public Optional<DocumentRecord> findByStoredFileId(UUID storedFileId) {
        return one("SELECT " + DOCUMENT_COLUMNS + " FROM document_record WHERE stored_file_id = ?", storedFileId);
    }

    @Override
    public boolean moveToQueued(UUID documentId, Instant now) {
        return jdbcTemplate.update("""
            UPDATE document_record
               SET state = 'QUEUED', updated_at = ?
             WHERE id = ? AND state = 'UPLOADED'
            """, time(now), documentId) == 1;
    }

    @Override
    public boolean claimProcessing(UUID documentId, UUID processingToken, Instant now, Instant staleBefore) {
        return jdbcTemplate.update("""
            UPDATE document_record
               SET state = 'PROCESSING', processing_attempts = processing_attempts + 1,
                   processing_token = ?, processing_started_at = ?, last_error = NULL, updated_at = ?
             WHERE id = ?
               AND (
                    state IN ('QUEUED', 'FAILED')
                    OR (state = 'PROCESSING' AND processing_started_at < ?)
               )
            """, processingToken, time(now), time(now), documentId, time(staleBefore)) == 1;
    }

    @Override
    public boolean markFailed(UUID documentId, UUID processingToken, String error, Instant now) {
        return jdbcTemplate.update("""
            UPDATE document_record
               SET state = 'FAILED', processing_token = NULL, processing_started_at = NULL,
                   last_error = ?, updated_at = ?
             WHERE id = ? AND state = 'PROCESSING' AND processing_token = ?
            """, error, time(now), documentId, processingToken) == 1;
    }

    @Override
    public boolean completeExtraction(
            UUID documentId, UUID processingToken, DocumentExtraction extraction, Instant completedAt) {
        int claimed = jdbcTemplate.update("""
            UPDATE document_record
               SET state = 'PARSED', processing_token = NULL, processing_started_at = NULL,
                   last_error = NULL, updated_at = ?
             WHERE id = ? AND state = 'PROCESSING' AND processing_token = ?
            """, time(completedAt), documentId, processingToken);
        if (claimed != 1) {
            return false;
        }

        jdbcTemplate.update(
                """
            INSERT INTO document_extraction (
                id, document_id, provider_name, parser_version, raw_result_reference, completed_at
            ) VALUES (?, ?, ?, ?, ?, ?)
            """,
                extraction.id(),
                documentId,
                extraction.providerName(),
                extraction.parserVersion(),
                extraction.rawResultReference(),
                time(extraction.completedAt()));
        for (ExtractedDocumentField field : extraction.fields()) {
            jdbcTemplate.update(
                    """
                INSERT INTO document_extracted_field (
                    extraction_id, field_path, field_value, confidence, source_page, source_region
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                    extraction.id(),
                    field.path().strip(),
                    field.value(),
                    field.confidence(),
                    field.sourcePage(),
                    field.sourceRegion());
        }
        return true;
    }

    @Override
    public void addTransition(
            UUID documentId,
            DocumentState fromState,
            DocumentState toState,
            String reason,
            String actor,
            Instant occurredAt) {
        jdbcTemplate.update(
                """
            INSERT INTO document_state_transition (
                document_id, from_state, to_state, reason, actor, occurred_at
            ) VALUES (?, ?, ?, ?, ?, ?)
            """,
                documentId,
                fromState == null ? null : fromState.name(),
                toState.name(),
                reason,
                actor,
                time(occurredAt));
    }

    private Optional<DocumentConfirmation> findConfirmationByRequestId(UUID documentId, UUID requestId) {
        List<DocumentConfirmation> rows = jdbcTemplate.query("""
            SELECT id, document_id, request_id, revision, confirmed_by_user_id, created_at
              FROM document_confirmation
             WHERE document_id = ? AND request_id = ?
            """, this::mapConfirmation, documentId, requestId);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<ConfirmationWrite> addConfirmation(
            UUID documentId, UUID requestId, UUID confirmedByUserId, List<ConfirmedDocumentField> fields, Instant now) {
        List<DocumentState> states = jdbcTemplate.query(
                "SELECT state FROM document_record WHERE id = ? FOR UPDATE",
                (resultSet, rowNumber) -> DocumentState.valueOf(resultSet.getString("state")),
                documentId);
        if (states.isEmpty()
                || (states.getFirst() != DocumentState.PARSED && states.getFirst() != DocumentState.CONFIRMED)) {
            return Optional.empty();
        }

        Optional<DocumentConfirmation> existing = findConfirmationByRequestId(documentId, requestId);
        if (existing.isPresent()) {
            return Optional.of(new ConfirmationWrite(existing.get(), false, false));
        }

        Integer latest = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(revision), 0) FROM document_confirmation WHERE document_id = ?",
                Integer.class,
                documentId);
        int revision = (latest == null ? 0 : latest) + 1;
        UUID confirmationId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO document_confirmation (
                id, document_id, request_id, revision, confirmed_by_user_id, created_at
            ) VALUES (?, ?, ?, ?, ?, ?)
            """, confirmationId, documentId, requestId, revision, confirmedByUserId, time(now));
        for (ConfirmedDocumentField field : fields) {
            jdbcTemplate.update("""
                INSERT INTO document_confirmed_field (confirmation_id, field_path, field_value)
                VALUES (?, ?, ?)
                """, confirmationId, field.path(), field.value());
        }
        boolean transitioned = states.getFirst() == DocumentState.PARSED;
        if (transitioned) {
            jdbcTemplate.update(
                    "UPDATE document_record SET state = 'CONFIRMED', updated_at = ? WHERE id = ? AND state = 'PARSED'",
                    time(now),
                    documentId);
        }
        return Optional.of(new ConfirmationWrite(
                new DocumentConfirmation(
                        confirmationId, documentId, requestId, revision, confirmedByUserId, fields, now),
                true,
                transitioned));
    }

    @Override
    public Optional<DocumentView> loadView(UUID documentId, UUID businessId) {
        Optional<DocumentRecord> document = findByIdAndBusinessId(documentId, businessId);
        if (document.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new DocumentView(
                document.get(), extraction(documentId), latestConfirmation(documentId), transitions(documentId)));
    }

    private Optional<DocumentExtraction> extraction(UUID documentId) {
        List<ExtractionHeader> rows = jdbcTemplate.query(
                """
            SELECT id, document_id, provider_name, parser_version, raw_result_reference, completed_at
              FROM document_extraction WHERE document_id = ?
            """,
                (resultSet, rowNumber) -> new ExtractionHeader(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("document_id", UUID.class),
                        resultSet.getString("provider_name"),
                        resultSet.getString("parser_version"),
                        resultSet.getString("raw_result_reference"),
                        instant(resultSet, "completed_at")),
                documentId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        ExtractionHeader header = rows.getFirst();
        List<ExtractedDocumentField> fields = jdbcTemplate.query(
                """
            SELECT field_path, field_value, confidence, source_page, source_region
              FROM document_extracted_field WHERE extraction_id = ? ORDER BY field_path
            """,
                (resultSet, rowNumber) -> new ExtractedDocumentField(
                        resultSet.getString("field_path"),
                        resultSet.getString("field_value"),
                        resultSet.getBigDecimal("confidence"),
                        resultSet.getObject("source_page", Integer.class),
                        resultSet.getString("source_region")),
                header.id());
        return Optional.of(new DocumentExtraction(
                header.id(),
                header.documentId(),
                header.providerName(),
                header.parserVersion(),
                header.rawResultReference(),
                fields,
                header.completedAt()));
    }

    private Optional<DocumentConfirmation> latestConfirmation(UUID documentId) {
        List<DocumentConfirmation> rows = jdbcTemplate.query("""
            SELECT id, document_id, request_id, revision, confirmed_by_user_id, created_at
              FROM document_confirmation
             WHERE document_id = ?
             ORDER BY revision DESC
             LIMIT 1
            """, this::mapConfirmation, documentId);
        return rows.stream().findFirst();
    }

    private DocumentConfirmation mapConfirmation(ResultSet resultSet, int rowNumber) throws SQLException {
        UUID confirmationId = resultSet.getObject("id", UUID.class);
        List<ConfirmedDocumentField> fields = jdbcTemplate.query(
                """
            SELECT field_path, field_value
              FROM document_confirmed_field WHERE confirmation_id = ? ORDER BY field_path
            """,
                (fieldSet, fieldNumber) ->
                        new ConfirmedDocumentField(fieldSet.getString("field_path"), fieldSet.getString("field_value")),
                confirmationId);
        return new DocumentConfirmation(
                confirmationId,
                resultSet.getObject("document_id", UUID.class),
                resultSet.getObject("request_id", UUID.class),
                resultSet.getInt("revision"),
                resultSet.getObject("confirmed_by_user_id", UUID.class),
                fields,
                instant(resultSet, "created_at"));
    }

    private List<DocumentStateTransition> transitions(UUID documentId) {
        return jdbcTemplate.query(
                """
            SELECT from_state, to_state, reason, actor, occurred_at
              FROM document_state_transition WHERE document_id = ? ORDER BY occurred_at, id
            """,
                (resultSet, rowNumber) -> new DocumentStateTransition(
                        resultSet.getString("from_state") == null
                                ? null
                                : DocumentState.valueOf(resultSet.getString("from_state")),
                        DocumentState.valueOf(resultSet.getString("to_state")),
                        resultSet.getString("reason"),
                        resultSet.getString("actor"),
                        instant(resultSet, "occurred_at")),
                documentId);
    }

    private Optional<DocumentRecord> one(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, this::mapDocument, parameters).stream().findFirst();
    }

    private DocumentRecord mapDocument(ResultSet resultSet, int rowNumber) throws SQLException {
        return new DocumentRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("business_id", UUID.class),
                resultSet.getObject("stored_file_id", UUID.class),
                DocumentType.valueOf(resultSet.getString("document_type")),
                DocumentState.valueOf(resultSet.getString("state")),
                resultSet.getInt("processing_attempts"),
                resultSet.getString("last_error"),
                resultSet.getObject("created_by_user_id", UUID.class),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private static OffsetDateTime time(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private record ExtractionHeader(
            UUID id,
            UUID documentId,
            String providerName,
            String parserVersion,
            String rawResultReference,
            Instant completedAt) {}
}
