package za.co.trademesh.modules.evidence.infrastructure;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.trademesh.modules.evidence.application.BusinessTrustEvidenceCatalog;

@Repository
class JdbcBusinessTrustEvidenceCatalog implements BusinessTrustEvidenceCatalog {

    private final JdbcTemplate jdbcTemplate;

    JdbcBusinessTrustEvidenceCatalog(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Version-one completion rules:
     *
     * <ul>
     *   <li>The shipment must have an immutable SHIPMENT_CREATED record naming the business.</li>
     *   <li>The latest evidenced status is authoritative.</li>
     *   <li>DELIVERED and DISPUTED are completed outcomes; only DELIVERED is successful.</li>
     *   <li>CANCELLED is excluded because no completed delivery is proven.</li>
     * </ul>
     */
    @Override
    public CompletionStats completionStats(UUID businessId) {
        return jdbcTemplate.queryForObject(
                """
                WITH owned_shipments AS (
                    SELECT subject_id AS shipment_id
                      FROM evidence_record
                     WHERE evidence_type = 'SHIPMENT_CREATED'
                       AND metadata ->> 'requestedByBusinessId' = ?
                ),
                latest_status AS (
                    SELECT DISTINCT ON (record.subject_id)
                           record.subject_id,
                           record.metadata ->> 'toStatus' AS status
                      FROM evidence_record record
                      JOIN owned_shipments owned ON owned.shipment_id = record.subject_id
                     WHERE record.evidence_type = 'SHIPMENT_STATUS_CHANGED'
                     ORDER BY record.subject_id, record.occurred_at DESC, record.ledger_sequence DESC
                ),
                source_high_water AS (
                    SELECT COALESCE(MAX(record.ledger_sequence), 0) AS sequence
                      FROM evidence_record record
                      JOIN owned_shipments owned ON owned.shipment_id = record.shipment_id
                )
                SELECT (SELECT COUNT(*)
                          FROM latest_status
                         WHERE status IN ('DELIVERED', 'DISPUTED')) AS completed,
                       (SELECT COUNT(*)
                          FROM latest_status
                         WHERE status = 'DELIVERED') AS successful,
                       (SELECT sequence FROM source_high_water) AS sequence
                """,
                (resultSet, rowNumber) -> new CompletionStats(
                        resultSet.getInt("completed"), resultSet.getInt("successful"), resultSet.getLong("sequence")),
                businessId.toString());
    }
}
