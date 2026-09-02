package za.co.trademesh.modules.handover.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.trademesh.modules.handover.application.HandoverNotificationRecipients;

@Repository
class JdbcHandoverNotificationRecipients implements HandoverNotificationRecipients {

    private final JdbcTemplate jdbcTemplate;

    JdbcHandoverNotificationRecipients(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Participants> find(UUID challengeId) {
        return jdbcTemplate
                .query(
                        """
            SELECT initiator_user_id, counterparty_user_id
              FROM handover_challenge
             WHERE id = ?
            """,
                        (resultSet, rowNumber) -> new Participants(
                                resultSet.getObject("initiator_user_id", UUID.class),
                                resultSet.getObject("counterparty_user_id", UUID.class)),
                        challengeId)
                .stream()
                .findFirst();
    }
}
