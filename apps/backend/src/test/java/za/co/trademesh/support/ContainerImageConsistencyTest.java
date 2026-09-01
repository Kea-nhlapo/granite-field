package za.co.trademesh.support;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the one fact that lives in two places. If the compose image and the
 * test container drift, PostGIS is present in one environment and absent in
 * the other, and the migration fails somewhere nobody is looking.
 *
 * <p>Needs no Docker daemon.
 */
class ContainerImageConsistencyTest {

    private static final Path COMPOSE_FILE =
        Path.of("..", "..", "infra", "containers", "docker-compose.yml");

    @Test
    void composeAndTestContainerUseTheSameImage() throws IOException {
        assertThat(COMPOSE_FILE).exists();

        List<String> lines = Files.readAllLines(COMPOSE_FILE);
        Optional<String> declared = lines.stream()
            .map(String::trim)
            .filter(line -> line.startsWith("image:"))
            .map(line -> line.substring("image:".length()).trim())
            .findFirst();

        assertThat(declared).as("docker-compose.yml declares an image").isPresent();
        assertThat(declared.get()).isEqualTo(PostgresIntegrationTest.POSTGRES_IMAGE);
    }
}
