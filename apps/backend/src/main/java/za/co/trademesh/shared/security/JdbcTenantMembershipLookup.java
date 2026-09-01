package za.co.trademesh.shared.security;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTenantMembershipLookup implements TenantMembershipLookup {

    private final JdbcTemplate jdbcTemplate;

    JdbcTenantMembershipLookup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isMember(UUID userId, UUID businessId) {
        Boolean found = jdbcTemplate.queryForObject("""
            SELECT EXISTS (
                SELECT 1
                FROM access_business_membership
                WHERE user_id = ? AND business_id = ?
            )
            """, Boolean.class, userId, businessId);
        return Boolean.TRUE.equals(found);
    }
}
