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
import za.co.trademesh.modules.document.domain.DocumentComparison;
import za.co.trademesh.modules.document.domain.DocumentComparisonRepository;
import za.co.trademesh.modules.document.domain.DocumentComparisonSource;
import za.co.trademesh.modules.document.domain.DocumentMismatchIndicator;
import za.co.trademesh.modules.document.domain.DocumentMismatchRule;
import za.co.trademesh.modules.document.domain.DocumentMismatchSeverity;
import za.co.trademesh.modules.document.domain.DocumentType;

@Repository
class JdbcDocumentComparisonRepository implements DocumentComparisonRepository {

    private static final String COMPARISON_COLUMNS = """
        id, business_id, rule_set_version,
        reference_document_id, reference_document_type,
        reference_confirmation_id, reference_confirmation_revision,
        compared_document_id, compared_document_type,
        compared_confirmation_id, compared_confirmation_revision,
        created_by_user_id, created_at
        """;

    private final JdbcTemplate jdbcTemplate;

    JdbcDocumentComparisonRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean save(DocumentComparison comparison, UUID clientRequestId) {
        int written = jdbcTemplate.update(
                """
            INSERT INTO document_comparison (
                id, business_id, client_request_id, rule_set_version,
                reference_document_id, reference_document_type,
                reference_confirmation_id, reference_confirmation_revision,
                compared_document_id, compared_document_type,
                compared_confirmation_id, compared_confirmation_revision,
                created_by_user_id, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """,
                comparison.id(),
                comparison.businessId(),
                clientRequestId,
                comparison.ruleSetVersion(),
                comparison.reference().documentId(),
                comparison.reference().documentType().name(),
                comparison.reference().confirmationId(),
                comparison.reference().confirmationRevision(),
                comparison.compared().documentId(),
                comparison.compared().documentType().name(),
                comparison.compared().confirmationId(),
                comparison.compared().confirmationRevision(),
                comparison.createdByUserId(),
                time(comparison.createdAt()));
        if (written != 1) {
            return false;
        }
        for (DocumentMismatchIndicator indicator : comparison.indicators()) {
            jdbcTemplate.update(
                    """
                INSERT INTO document_mismatch_indicator (
                    id, comparison_id, rule_code, rule_version, field_path, severity,
                    reference_document_id, reference_value, compared_document_id,
                    compared_value, explanation, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                    indicator.id(),
                    comparison.id(),
                    indicator.rule().name(),
                    indicator.ruleVersion(),
                    indicator.fieldPath(),
                    indicator.severity().name(),
                    indicator.referenceDocumentId(),
                    indicator.referenceValue(),
                    indicator.comparedDocumentId(),
                    indicator.comparedValue(),
                    indicator.explanation(),
                    time(indicator.createdAt()));
        }
        return true;
    }

    @Override
    public Optional<DocumentComparison> findById(UUID businessId, UUID comparisonId) {
        return one(
                "SELECT " + COMPARISON_COLUMNS + " FROM document_comparison WHERE business_id = ? AND id = ?",
                businessId,
                comparisonId);
    }

    @Override
    public Optional<DocumentComparison> findByClientRequestId(UUID businessId, UUID clientRequestId) {
        return one(
                "SELECT " + COMPARISON_COLUMNS
                        + " FROM document_comparison WHERE business_id = ? AND client_request_id = ?",
                businessId,
                clientRequestId);
    }

    @Override
    public Optional<DocumentComparison> findByScope(
            UUID businessId, UUID referenceConfirmationId, UUID comparedConfirmationId, String ruleSetVersion) {
        return one(
                "SELECT " + COMPARISON_COLUMNS
                        + " FROM document_comparison WHERE business_id = ? AND reference_confirmation_id = ?"
                        + " AND compared_confirmation_id = ? AND rule_set_version = ?",
                businessId,
                referenceConfirmationId,
                comparedConfirmationId,
                ruleSetVersion);
    }

    private Optional<DocumentComparison> one(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, this::mapComparison, parameters).stream().findFirst();
    }

    private DocumentComparison mapComparison(ResultSet resultSet, int rowNumber) throws SQLException {
        UUID comparisonId = resultSet.getObject("id", UUID.class);
        List<DocumentMismatchIndicator> indicators = jdbcTemplate.query("""
            SELECT id, rule_code, rule_version, field_path, severity,
                   reference_document_id, reference_value, compared_document_id,
                   compared_value, explanation, created_at
              FROM document_mismatch_indicator
             WHERE comparison_id = ?
             ORDER BY rule_code, field_path
            """, this::mapIndicator, comparisonId);
        return new DocumentComparison(
                comparisonId,
                resultSet.getObject("business_id", UUID.class),
                resultSet.getString("rule_set_version"),
                new DocumentComparisonSource(
                        resultSet.getObject("reference_document_id", UUID.class),
                        DocumentType.valueOf(resultSet.getString("reference_document_type")),
                        resultSet.getObject("reference_confirmation_id", UUID.class),
                        resultSet.getInt("reference_confirmation_revision")),
                new DocumentComparisonSource(
                        resultSet.getObject("compared_document_id", UUID.class),
                        DocumentType.valueOf(resultSet.getString("compared_document_type")),
                        resultSet.getObject("compared_confirmation_id", UUID.class),
                        resultSet.getInt("compared_confirmation_revision")),
                indicators,
                resultSet.getObject("created_by_user_id", UUID.class),
                instant(resultSet, "created_at"));
    }

    private DocumentMismatchIndicator mapIndicator(ResultSet resultSet, int rowNumber) throws SQLException {
        return new DocumentMismatchIndicator(
                resultSet.getObject("id", UUID.class),
                DocumentMismatchRule.valueOf(resultSet.getString("rule_code")),
                resultSet.getInt("rule_version"),
                resultSet.getString("field_path"),
                DocumentMismatchSeverity.valueOf(resultSet.getString("severity")),
                resultSet.getObject("reference_document_id", UUID.class),
                resultSet.getString("reference_value"),
                resultSet.getObject("compared_document_id", UUID.class),
                resultSet.getString("compared_value"),
                resultSet.getString("explanation"),
                instant(resultSet, "created_at"));
    }

    private static OffsetDateTime time(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }
}
