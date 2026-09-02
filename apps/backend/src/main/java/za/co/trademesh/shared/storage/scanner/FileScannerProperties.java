package za.co.trademesh.shared.storage.scanner;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Selects the {@link za.co.trademesh.shared.storage.FileScanner} implementation.
 *
 * <p>Selection is a property rather than a Spring profile so the scanner actually running in a
 * deployment is visible in that deployment's environment file. An unset provider resolves to
 * {@code fail-closed}: a misconfigured environment refuses uploads instead of silently accepting
 * unscanned ones.
 */
@ConfigurationProperties("trademesh.storage.file-scanner")
public record FileScannerProperties(String provider, ClamAv clamav) {

    public static final String FAIL_CLOSED = "fail-closed";
    public static final String MOCK = "mock";
    public static final String CLAMAV = "clamav";

    public FileScannerProperties {
        if (provider == null || provider.isBlank()) {
            provider = FAIL_CLOSED;
        }
        if (clamav == null) {
            clamav = new ClamAv(null, 0, null);
        }
    }

    /** Connection settings for a clamd daemon reachable over TCP. */
    public record ClamAv(String host, int port, Duration timeout) {

        private static final String DEFAULT_HOST = "clamav";
        private static final int DEFAULT_PORT = 3310;
        private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

        public ClamAv {
            if (host == null || host.isBlank()) {
                host = DEFAULT_HOST;
            }
            if (port <= 0 || port > 65535) {
                port = DEFAULT_PORT;
            }
            if (timeout == null || timeout.isNegative() || timeout.isZero()) {
                timeout = DEFAULT_TIMEOUT;
            }
        }
    }
}
