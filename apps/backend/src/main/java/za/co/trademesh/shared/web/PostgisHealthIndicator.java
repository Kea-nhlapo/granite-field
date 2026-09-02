package za.co.trademesh.shared.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("postgis")
public class PostgisHealthIndicator implements HealthIndicator {

    private final ObjectProvider<JdbcTemplate> jdbcTemplates;

    public PostgisHealthIndicator(ObjectProvider<JdbcTemplate> jdbcTemplates) {
        this.jdbcTemplates = jdbcTemplates;
    }

    @Override
    public Health health() {
        try {
            JdbcTemplate jdbcTemplate = jdbcTemplates.getIfAvailable();
            if (jdbcTemplate == null) {
                return Health.unknown()
                        .withDetail("reason", "database-not-configured")
                        .build();
            }
            String version = jdbcTemplate.queryForObject("SELECT PostGIS_Version()", String.class);
            return Health.up().withDetail("version", version).build();
        } catch (RuntimeException unavailable) {
            // Avoid returning JDBC details that may contain internal hostnames.
            return Health.down().build();
        }
    }
}
