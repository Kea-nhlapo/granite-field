package za.co.trademesh.modules.access.infrastructure;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.trademesh.modules.access.domain.BusinessMembershipRepository;

@Repository
class JdbcBusinessMembershipRepository implements BusinessMembershipRepository {

    private final JdbcTemplate jdbcTemplate;

    JdbcBusinessMembershipRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void grantOwner(UUID businessId, UUID userId, Instant createdAt) {
        jdbcTemplate.update("""
            INSERT INTO access_business_membership (
                business_id, user_id, membership_role, membership_status, created_at
            ) VALUES (?, ?, 'BUSINESS_OWNER', 'ACTIVE', ?)
            ON CONFLICT (business_id, user_id) DO UPDATE
            SET membership_role = 'BUSINESS_OWNER', membership_status = 'ACTIVE'
            """, businessId, userId, OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
    }
}
