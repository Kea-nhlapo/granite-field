package za.co.trademesh.modules.access.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.trademesh.modules.access.application.UserBusinessCatalog;

@Repository
class JdbcUserBusinessCatalog implements UserBusinessCatalog {

    private final JdbcTemplate jdbcTemplate;

    JdbcUserBusinessCatalog(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UUID> findPrimaryBusinessId(UUID userId) {
        return jdbcTemplate
                .query("""
                        SELECT business_id
                          FROM access_business_membership
                         WHERE user_id = ?
                           AND membership_status = 'ACTIVE'
                         ORDER BY CASE membership_role WHEN 'BUSINESS_OWNER' THEN 0 ELSE 1 END,
                                  created_at,
                                  business_id
                         LIMIT 1
                        """, (resultSet, rowNumber) -> resultSet.getObject("business_id", UUID.class), userId)
                .stream()
                .findFirst();
    }
}
