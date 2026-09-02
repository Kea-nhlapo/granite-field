package za.co.trademesh.modules.access.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.trademesh.modules.access.application.BusinessNotificationRecipients;

@Repository
class JdbcBusinessNotificationRecipients implements BusinessNotificationRecipients {

    private final JdbcTemplate jdbcTemplate;

    JdbcBusinessNotificationRecipients(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<UUID> findActiveUserIds(UUID businessId) {
        return jdbcTemplate.query(
                """
            SELECT membership.user_id
              FROM access_business_membership membership
              JOIN access_user_account account ON account.id = membership.user_id
             WHERE membership.business_id = ?
               AND membership.membership_status = 'ACTIVE'
               AND account.enabled
             ORDER BY CASE membership.membership_role WHEN 'BUSINESS_OWNER' THEN 0 ELSE 1 END,
                      membership.created_at,
                      membership.user_id
            """, (resultSet, rowNumber) -> resultSet.getObject("user_id", UUID.class), businessId);
    }
}
