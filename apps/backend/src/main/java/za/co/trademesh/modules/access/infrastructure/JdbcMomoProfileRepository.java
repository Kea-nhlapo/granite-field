package za.co.trademesh.modules.access.infrastructure;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.trademesh.modules.access.application.MomoProfileRepository;
import za.co.trademesh.modules.payment.application.MomoClient;

@Repository
class JdbcMomoProfileRepository implements MomoProfileRepository {

    private final JdbcTemplate jdbcTemplate;

    JdbcMomoProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(UUID userId, String phoneNumber, MomoClient.UserInfo userInfo, Instant verifiedAt) {
        jdbcTemplate.update(
                """
            INSERT INTO access_momo_profile (
                user_id, phone_number, given_name, family_name, locale, verified_at
            ) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id) DO UPDATE SET
                phone_number = EXCLUDED.phone_number,
                given_name = EXCLUDED.given_name,
                family_name = EXCLUDED.family_name,
                locale = EXCLUDED.locale,
                verified_at = EXCLUDED.verified_at
            """,
                userId,
                phoneNumber,
                userInfo.givenName(),
                userInfo.familyName(),
                userInfo.locale(),
                OffsetDateTime.ofInstant(verifiedAt, ZoneOffset.UTC));
    }
}
