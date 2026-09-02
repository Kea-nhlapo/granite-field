package za.co.trademesh.modules.access.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.trademesh.modules.access.application.VerifiedPhoneCatalog;

@Repository
class JdbcVerifiedPhoneCatalog implements VerifiedPhoneCatalog {

    private final JdbcTemplate jdbcTemplate;

    JdbcVerifiedPhoneCatalog(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<String> findPrimaryForBusiness(UUID businessId) {
        return jdbcTemplate
                .query("""
                SELECT phone.phone_number
                  FROM access_business_membership membership
                 JOIN access_phone_identity phone ON phone.user_id = membership.user_id
                 WHERE membership.business_id = ?
                   AND membership.membership_status = 'ACTIVE'
                 ORDER BY CASE membership.membership_role WHEN 'BUSINESS_OWNER' THEN 0 ELSE 1 END,
                          membership.created_at,
                          phone.phone_number
                 LIMIT 1
                """, (resultSet, rowNumber) -> resultSet.getString("phone_number"), businessId)
                .stream()
                .findFirst();
    }
}
