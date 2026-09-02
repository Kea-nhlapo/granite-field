package za.co.trademesh.modules.evidence.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
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

    @Override
    public ScoreHistory scoreHistory(UUID businessId) {
        List<ScoreEvent> events = jdbcTemplate.query(
                """
                WITH owned_shipments AS (
                    SELECT subject_id AS shipment_id
                      FROM evidence_record
                     WHERE evidence_type = 'SHIPMENT_CREATED'
                       AND metadata ->> 'requestedByBusinessId' = ?
                )
                SELECT record.evidence_type,
                       COALESCE(record.metadata ->> 'outcome', record.metadata ->> 'rule', '') AS outcome,
                       record.occurred_at,
                       record.ledger_sequence
                  FROM evidence_record record
                 WHERE (
                           record.evidence_type = 'business.profile-confirmed'
                           AND record.subject_type = 'BUSINESS'
                           AND record.subject_id = ?
                       )
                    OR (
                           record.shipment_id IN (SELECT shipment_id FROM owned_shipments)
                           AND (
                               (record.evidence_type = 'HANDOVER_FINALIZED'
                                AND record.metadata ->> 'handoverType' = 'DELIVERY')
                               OR record.evidence_type IN (
                                   'ESCROW_LOCKED',
                                   'ESCROW_LOCK_FAILED',
                                   'ESCROW_RELEASED',
                                   'ESCROW_RELEASE_FAILED'
                               )
                               OR (record.evidence_type = 'RISK_INDICATOR_OPENED'
                                   AND record.metadata ->> 'rule' = 'ROUTE_DEVIATION')
                           )
                       )
                 ORDER BY record.occurred_at, record.ledger_sequence
                """,
                (resultSet, rowNumber) -> new ScoreEvent(
                        resultSet.getString("evidence_type"),
                        resultSet.getString("outcome"),
                        resultSet.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        resultSet.getLong("ledger_sequence")),
                businessId.toString(),
                businessId);
        long sourceThroughSequence =
                events.stream().mapToLong(ScoreEvent::ledgerSequence).max().orElse(0L);
        return new ScoreHistory(events, sourceThroughSequence);
    }
}
