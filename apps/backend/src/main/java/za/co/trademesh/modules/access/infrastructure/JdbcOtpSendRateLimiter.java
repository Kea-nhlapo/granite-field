package za.co.trademesh.modules.access.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.trademesh.modules.access.application.OtpSendRateLimiter;

@Repository
class JdbcOtpSendRateLimiter implements OtpSendRateLimiter {

    private final JdbcTemplate jdbcTemplate;

    JdbcOtpSendRateLimiter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean acquire(String phoneNumber, Instant now, Duration cooldown) {
        int changed = jdbcTemplate.update(
                """
            INSERT INTO access_otp_send_limit (phone_hash, last_sent_at)
            VALUES (?, ?)
            ON CONFLICT (phone_hash) DO UPDATE
            SET last_sent_at = EXCLUDED.last_sent_at
            WHERE access_otp_send_limit.last_sent_at <= ?
            """,
                hash(phoneNumber),
                OffsetDateTime.ofInstant(now, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(now.minus(cooldown), ZoneOffset.UTC));
        return changed == 1;
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
