package za.co.trademesh.support;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the one fact that lives in two places. If the compose image and the
 * test container drift, PostGIS is present in one environment and absent in
 * the other, and the migration fails somewhere nobody is looking.
 *
 * <p>Needs no Docker daemon.
 */
class ContainerImageConsistencyTest {

    private static final Path COMPOSE_RELATIVE_PATH =
        Path.of("infra", "containers", "docker-compose.yml");

    private static final String SERVICE_NAME = "postgres";

    @Test
    void composeAndTestContainerUseTheSameImage() throws IOException {
        Path composeFile = locateComposeFile();

        try (InputStream yaml = Files.newInputStream(composeFile)) {
            Map<String, Object> root = new Yaml().load(yaml);
            Map<String, Object> services = section(root, "services", composeFile);
            Map<String, Object> postgres = section(services, SERVICE_NAME, composeFile);

            assertThat(postgres.get("image"))
                .as("services.%s.image in %s", SERVICE_NAME, composeFile)
                .isEqualTo(PostgresIntegrationTest.POSTGRES_IMAGE);
        }
    }

    /**
     * Walks up from the working directory rather than assuming a fixed number
     * of "..' segments. The working directory differs between Surefire (the
     * module basedir) and an IDE run configuration (often the repository root),
     * and a hardcoded relative path silently resolves to nothing in one of them.
     */
    private static Path locateComposeFile() {
        for (Path directory = Path.of("").toAbsolutePath();
             directory != null;
             directory = directory.getParent()) {

            Path candidate = directory.resolve(COMPOSE_RELATIVE_PATH);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new AssertionError(
            "no " + COMPOSE_RELATIVE_PATH + " found in any ancestor of " + Path.of("").toAbsolutePath());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> parent, String key, Path file) {
        Object value = parent == null ? null : parent.get(key);
        assertThat(value).as("%s is declared in %s", key, file).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }
}
