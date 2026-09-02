package za.co.trademesh.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ObjectStoragePropertiesTest {

    @Test
    void neverPrintsCredentialsAndKeepsPresignedAccessShortLived() {
        var properties = new ObjectStorageProperties(
                "https://storage.example",
                "private-key",
                "private-secret",
                "files",
                100,
                Duration.ofDays(30),
                "af-south-1");

        assertThat(properties.downloadTtl()).isEqualTo(Duration.ofMinutes(15));
        assertThat(properties.toString())
                .doesNotContain("private-key")
                .doesNotContain("private-secret")
                .contains("<redacted>");
    }

    @Test
    void treatsBlankRegionAsUnset() {
        var properties = new ObjectStorageProperties(
                "https://storage.example", "key", "secret", "files", 100, Duration.ofMinutes(5), "   ");

        assertThat(properties.region()).isNull();
    }
}
