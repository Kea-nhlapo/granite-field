package za.co.trademesh.shared.web;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("trademesh.web")
public record ApiWebProperties(List<String> allowedOrigins, Duration corsMaxAge, DataSize maximumContentLength) {

    public ApiWebProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        if (allowedOrigins.isEmpty()
                || allowedOrigins.stream().anyMatch(origin -> origin == null || origin.isBlank())) {
            throw new IllegalArgumentException("At least one explicit CORS origin is required");
        }
        if (allowedOrigins.contains("*")) {
            throw new IllegalArgumentException("Wildcard CORS origins are not allowed");
        }
        if (corsMaxAge == null || corsMaxAge.isNegative()) {
            throw new IllegalArgumentException("CORS max age must not be negative");
        }
        if (maximumContentLength == null || maximumContentLength.toBytes() <= 0) {
            throw new IllegalArgumentException("Maximum request content length must be positive");
        }
    }
}
