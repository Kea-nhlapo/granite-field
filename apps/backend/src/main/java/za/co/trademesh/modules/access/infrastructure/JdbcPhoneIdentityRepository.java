package za.co.trademesh.modules.access.infrastructure;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.trademesh.modules.access.application.PhoneIdentityRepository;

@Repository
class JdbcPhoneIdentityRepository implements PhoneIdentityRepository {

    private final JdbcTemplate jdbcTemplate;

    JdbcPhoneIdentityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UUID> findUserId(String phoneNumber) {
        List<UUID> values = jdbcTemplate.queryForList(
                "SELECT user_id FROM access_phone_identity WHERE phone_number = ?", UUID.class, phoneNumber);
        return values.stream().findFirst();
    }

    @Override
    public void save(String phoneNumber, UUID userId, VerificationMethod method, Instant verifiedAt) {
        jdbcTemplate.update(
                """
            INSERT INTO access_phone_identity (phone_number, user_id, verification_method, verified_at)
            VALUES (?, ?, ?, ?)
            """, phoneNumber, userId, method.name(), OffsetDateTime.ofInstant(verifiedAt, ZoneOffset.UTC));
    }
}
