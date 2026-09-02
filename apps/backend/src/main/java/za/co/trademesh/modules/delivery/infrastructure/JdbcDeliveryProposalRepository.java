package za.co.trademesh.modules.delivery.infrastructure;

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
import za.co.trademesh.modules.delivery.domain.DeliveryMobileChannel;
import za.co.trademesh.modules.delivery.domain.DeliveryProposal;
import za.co.trademesh.modules.delivery.domain.DeliveryProposalRepository;
import za.co.trademesh.modules.delivery.domain.DeliveryProposalStatus;

@Repository
class JdbcDeliveryProposalRepository implements DeliveryProposalRepository {

    private static final String COLUMNS = """
        id, business_id, shipment_id, client_request_id, input_fingerprint,
        recipient_email, recipient_phone, mobile_channel, status,
        expires_at, created_at, accepted_at
        """;

    private final JdbcTemplate jdbcTemplate;

    JdbcDeliveryProposalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<DeliveryProposal> findByShipment(UUID businessId, UUID shipmentId) {
        return one("WHERE business_id = ? AND shipment_id = ?", businessId, shipmentId);
    }

    @Override
    public Optional<DeliveryProposal> findByRequest(UUID businessId, UUID clientRequestId) {
        return one("WHERE business_id = ? AND client_request_id = ?", businessId, clientRequestId);
    }

    @Override
    public Optional<DeliveryProposal> findByTokenHash(String tokenHash) {
        return one("WHERE confirmation_token_hash = ?", tokenHash);
    }

    @Override
    public boolean save(DeliveryProposal proposal, String tokenHash) {
        return jdbcTemplate.update(
                        """
                        INSERT INTO delivery_proposal (
                            id, business_id, shipment_id, client_request_id, input_fingerprint,
                            recipient_email, recipient_phone, mobile_channel, confirmation_token_hash,
                            status, expires_at, created_at, accepted_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT DO NOTHING
                        """,
                        proposal.id(),
                        proposal.businessId(),
                        proposal.shipmentId(),
                        proposal.clientRequestId(),
                        proposal.inputFingerprint(),
                        proposal.recipientEmail(),
                        proposal.recipientPhone(),
                        proposal.mobileChannel().name(),
                        tokenHash,
                        proposal.status().name(),
                        time(proposal.expiresAt()),
                        time(proposal.createdAt()),
                        time(proposal.acceptedAt()))
                == 1;
    }

    @Override
    public boolean accept(UUID proposalId, String tokenHash, Instant now) {
        return jdbcTemplate.update("""
                        UPDATE delivery_proposal
                           SET status = 'ACCEPTED', accepted_at = ?
                         WHERE id = ?
                           AND confirmation_token_hash = ?
                           AND status = 'PROPOSED'
                           AND expires_at > ?
                        """, time(now), proposalId, tokenHash, time(now)) == 1;
    }

    @Override
    public void expire(UUID proposalId, Instant now) {
        jdbcTemplate.update("""
                UPDATE delivery_proposal
                   SET status = 'EXPIRED'
                 WHERE id = ? AND status = 'PROPOSED' AND expires_at <= ?
                """, proposalId, time(now));
    }

    private Optional<DeliveryProposal> one(String where, Object... parameters) {
        List<DeliveryProposal> rows =
                jdbcTemplate.query("SELECT " + COLUMNS + " FROM delivery_proposal " + where, this::map, parameters);
        return rows.stream().findFirst();
    }

    private DeliveryProposal map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new DeliveryProposal(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("business_id", UUID.class),
                resultSet.getObject("shipment_id", UUID.class),
                resultSet.getObject("client_request_id", UUID.class),
                resultSet.getString("input_fingerprint"),
                resultSet.getString("recipient_email"),
                resultSet.getString("recipient_phone"),
                DeliveryMobileChannel.valueOf(resultSet.getString("mobile_channel")),
                DeliveryProposalStatus.valueOf(resultSet.getString("status")),
                instant(resultSet, "expires_at"),
                instant(resultSet, "created_at"),
                instant(resultSet, "accepted_at"));
    }

    private static OffsetDateTime time(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
