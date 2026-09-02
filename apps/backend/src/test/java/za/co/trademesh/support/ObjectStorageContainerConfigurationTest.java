package za.co.trademesh.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class ObjectStorageContainerConfigurationTest {

    private static final Path COMPOSE_RELATIVE_PATH = Path.of("infra", "containers", "docker-compose.yml");

    @Test
    void localMinioUsesAPinnedImageAndLoopbackOnlyPorts() throws IOException {
        Path composeFile = locateComposeFile();

        try (InputStream yaml = Files.newInputStream(composeFile)) {
            Map<String, Object> root = new Yaml().load(yaml);
            Map<String, Object> services = section(root, "services", composeFile);
            Map<String, Object> minio = section(services, "minio", composeFile);

            assertThat(minio.get("image").toString())
                    .startsWith("quay.io/minio/minio:RELEASE.")
                    .doesNotEndWith(":latest");
            assertThat(minio.get("command").toString()).contains("server /data");
            assertThat(list(minio, "ports", composeFile)).hasSize(2).allSatisfy(port -> assertThat(port.toString())
                    .startsWith("127.0.0.1:"));
        }
    }

    private static Path locateComposeFile() {
        for (Path directory = Path.of("").toAbsolutePath(); directory != null; directory = directory.getParent()) {
            Path candidate = directory.resolve(COMPOSE_RELATIVE_PATH);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new AssertionError("no " + COMPOSE_RELATIVE_PATH + " found");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> parent, String key, Path file) {
        Object value = parent == null ? null : parent.get(key);
        assertThat(value).as("%s is declared in %s", key, file).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Map<String, Object> parent, String key, Path file) {
        Object value = parent.get(key);
        assertThat(value).as("%s is declared in %s", key, file).isInstanceOf(List.class);
        return (List<Object>) value;
    }
}
