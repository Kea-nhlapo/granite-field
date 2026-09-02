package za.co.trademesh.modules.supplier.infrastructure;

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
import za.co.trademesh.modules.supplier.domain.SupplierEmail;
import za.co.trademesh.modules.supplier.domain.SupplierInvitation;
import za.co.trademesh.modules.supplier.domain.SupplierInvitationPurpose;
import za.co.trademesh.modules.supplier.domain.SupplierInvitationRepository;
import za.co.trademesh.modules.supplier.domain.SupplierInvitationStatus;
import za.co.trademesh.modules.supplier.domain.SupplierProfile;
import za.co.trademesh.modules.supplier.domain.SupplierProfileStatus;

@Repository
class JdbcSupplierInvitationRepository implements SupplierInvitationRepository {

    private final JdbcTemplate jdbcTemplate;

    JdbcSupplierInvitationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SupplierProfile getOrCreateTemporaryProfile(SupplierEmail email, UUID proposedId, Instant now) {
        jdbcTemplate.update("""
            INSERT INTO supplier_profile (
                id, normalized_email, profile_status, claimed_user_id,
                business_id, created_at, converted_at
            ) VALUES (?, ?, 'TEMPORARY', NULL, NULL, ?, NULL)
            ON CONFLICT (normalized_email) DO NOTHING
            """, proposedId, email.value(), time(now));
        return findProfileByEmail(email).orElseThrow();
    }

    @Override
    public Optional<SupplierProfile> findProfileById(UUID profileId) {
        return findOneProfile("WHERE id = ?", profileId);
    }

    private Optional<SupplierProfile> findProfileByEmail(SupplierEmail email) {
        return findOneProfile("WHERE normalized_email = ?", email.value());
    }

    @Override
    public Optional<SupplierInvitation> findInvitationById(UUID invitationId) {
        return findOneInvitation("WHERE id = ?", invitationId);
    }

    @Override
    public Optional<SupplierInvitation> findInvitationByTokenHash(String tokenHash) {
        return findOneInvitation("WHERE token_hash = ?", tokenHash);
    }

    @Override
    public boolean activeInvitationExists(UUID buyerBusinessId, UUID requestId, UUID supplierProfileId, Instant now) {
        Boolean exists = jdbcTemplate.queryForObject(
                """
            SELECT EXISTS (
                SELECT 1
                FROM supplier_invitation
                WHERE buyer_business_id = ?
                  AND request_id = ?
                  AND supplier_profile_id = ?
                  AND status = 'PENDING'
                  AND expires_at > ?
            )
            """, Boolean.class, buyerBusinessId, requestId, supplierProfileId, time(now));
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public void expirePendingForScope(UUID buyerBusinessId, UUID requestId, UUID supplierProfileId, Instant now) {
        jdbcTemplate.update("""
            UPDATE supplier_invitation
            SET status = 'EXPIRED', updated_at = ?
            WHERE buyer_business_id = ?
              AND request_id = ?
              AND supplier_profile_id = ?
              AND status = 'PENDING'
              AND expires_at <= ?
            """, time(now), buyerBusinessId, requestId, supplierProfileId, time(now));
    }

    @Override
    public void saveInvitation(SupplierInvitation invitation, String tokenHash) {
        jdbcTemplate.update(
                """
            INSERT INTO supplier_invitation (
                id, buyer_business_id, supplier_profile_id, request_id, purpose,
                token_hash, status, expires_at, response_reference, created_at,
                updated_at, responded_at, revoked_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
                invitation.id(),
                invitation.buyerBusinessId(),
                invitation.supplierProfileId(),
                invitation.requestId(),
                invitation.purpose().name(),
                tokenHash,
                invitation.status().name(),
                time(invitation.expiresAt()),
                invitation.responseReference(),
                time(invitation.createdAt()),
                time(invitation.createdAt()),
                time(invitation.respondedAt()),
                time(invitation.revokedAt()));
    }

    @Override
    public boolean recordResponse(
            UUID invitationId, String tokenHash, UUID requestId, UUID responseReference, Instant respondedAt) {
        return jdbcTemplate.update(
                        """
            UPDATE supplier_invitation
            SET status = 'RESPONDED', response_reference = ?, responded_at = ?, updated_at = ?
            WHERE id = ?
              AND token_hash = ?
              AND request_id = ?
              AND status = 'PENDING'
              AND expires_at > ?
            """,
                        responseReference,
                        time(respondedAt),
                        time(respondedAt),
                        invitationId,
                        tokenHash,
                        requestId,
                        time(respondedAt))
                == 1;
    }

    @Override
    public boolean revoke(UUID invitationId, UUID buyerBusinessId, Instant revokedAt) {
        return jdbcTemplate.update("""
            UPDATE supplier_invitation
            SET status = 'REVOKED', revoked_at = ?, updated_at = ?
            WHERE id = ? AND buyer_business_id = ? AND status = 'PENDING'
            """, time(revokedAt), time(revokedAt), invitationId, buyerBusinessId) == 1;
    }

    @Override
    public void markExpired(UUID invitationId, Instant now) {
        jdbcTemplate.update("""
            UPDATE supplier_invitation
            SET status = 'EXPIRED', updated_at = ?
            WHERE id = ? AND status = 'PENDING' AND expires_at <= ?
            """, time(now), invitationId, time(now));
    }

    @Override
    public boolean claimProfile(UUID profileId, UUID userId, UUID businessId, Instant convertedAt) {
        return jdbcTemplate.update("""
            UPDATE supplier_profile
            SET profile_status = 'REGISTERED', claimed_user_id = ?, business_id = ?, converted_at = ?
            WHERE id = ? AND profile_status = 'TEMPORARY'
            """, userId, businessId, time(convertedAt), profileId) == 1;
    }

    private Optional<SupplierProfile> findOneProfile(String whereClause, Object parameter) {
        List<SupplierProfile> rows = jdbcTemplate.query("""
            SELECT id, normalized_email, profile_status, claimed_user_id,
                   business_id, created_at, converted_at
            FROM supplier_profile
            """ + whereClause, this::mapProfile, parameter);
        return rows.stream().findFirst();
    }

    private Optional<SupplierInvitation> findOneInvitation(String whereClause, Object parameter) {
        List<SupplierInvitation> rows = jdbcTemplate.query("""
            SELECT id, buyer_business_id, supplier_profile_id, request_id, purpose,
                   status, expires_at, response_reference, created_at, responded_at, revoked_at
            FROM supplier_invitation
            """ + whereClause, this::mapInvitation, parameter);
        return rows.stream().findFirst();
    }

    private SupplierProfile mapProfile(ResultSet resultSet, int rowNumber) throws SQLException {
        return new SupplierProfile(
                resultSet.getObject("id", UUID.class),
                new SupplierEmail(resultSet.getString("normalized_email")),
                SupplierProfileStatus.valueOf(resultSet.getString("profile_status")),
                resultSet.getObject("claimed_user_id", UUID.class),
                resultSet.getObject("business_id", UUID.class),
                instant(resultSet, "created_at"),
                instant(resultSet, "converted_at"));
    }

    private SupplierInvitation mapInvitation(ResultSet resultSet, int rowNumber) throws SQLException {
        return new SupplierInvitation(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("buyer_business_id", UUID.class),
                resultSet.getObject("supplier_profile_id", UUID.class),
                resultSet.getObject("request_id", UUID.class),
                SupplierInvitationPurpose.valueOf(resultSet.getString("purpose")),
                SupplierInvitationStatus.valueOf(resultSet.getString("status")),
                instant(resultSet, "expires_at"),
                resultSet.getObject("response_reference", UUID.class),
                instant(resultSet, "created_at"),
                instant(resultSet, "responded_at"),
                instant(resultSet, "revoked_at"));
    }

    private static OffsetDateTime time(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
