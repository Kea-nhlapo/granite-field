package za.co.trademesh.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import za.co.trademesh.bootstrap.TradeMeshApplication;

/**
 * Base class for tests that need a real database.
 *
 * <p>The container is a singleton started once per JVM rather than per test
 * class: restarting PostgreSQL for every class costs seconds now and minutes
 * once feature modules add their own integration tests.
 *
 * <p>The configuration class is named explicitly because
 * {@link SpringBootTest} searches upward from the test's own package, and the
 * application class lives in the sibling package {@code bootstrap}. Tests in
 * {@code integration} or a feature module would otherwise fail to find it.
 */
@SpringBootTest(classes = TradeMeshApplication.class, properties =
    "trademesh.security.jwt.secret=test-only-auth-secret-32-characters")
public abstract class PostgresIntegrationTest {

    /**
     * Must match the image in infra/containers/docker-compose.yml so that local
     * development and tests agree about PostGIS availability.
     * ContainerImageConsistencyTest enforces this.
     */
    public static final String POSTGRES_IMAGE = "postgis/postgis:17-3.5";

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
        DockerImageName.parse(POSTGRES_IMAGE).asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }
}
