package za.co.trademesh.support;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces the migration naming agreed in issue #27: a UTC timestamp of digits
 * only, then the owning module.
 *
 * <p>The digits-only part is not cosmetic. Flyway version strings accept digits
 * separated by '.', '_' or '-'; an ISO-8601 'T' makes the version unparseable
 * and the application fails to start. A rule the build enforces survives; a
 * rule written only in a document does not.
 */
class MigrationNamingTest {

    private static final Path MIGRATIONS = Path.of("src", "main", "resources", "db", "migration");

    private static final Pattern VALID =
        Pattern.compile("^V[0-9]{14}__[a-z0-9]+(_[a-z0-9]+)*[.]sql$");

    @Test
    void everyMigrationFollowsTheAgreedNamingConvention() throws IOException {
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            List<String> names = files.map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith(".sql"))
                .toList();

            assertThat(names).isNotEmpty();
            assertThat(names).allSatisfy(name ->
                assertThat(name)
                    .as("%s must match V<yyyyMMddHHmmss>__<module>_<description>.sql", name)
                    .matches(VALID));
        }
    }
}
