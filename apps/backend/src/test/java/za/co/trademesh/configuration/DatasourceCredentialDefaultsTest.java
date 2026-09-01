package za.co.trademesh.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import za.co.trademesh.bootstrap.TradeMeshApplication;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * application.yml claims that the convenience datasource defaults are reachable
 * only under the {@code local} profile, so a shipped default password can never
 * reach a real database. That claim is load-bearing and was previously only a
 * comment — one accidentally added {@code :default} in the base document would
 * quietly reverse it with nothing to catch the change.
 *
 * <p>Needs no Docker daemon: neither test opens a connection.
 */
class DatasourceCredentialDefaultsTest {

    private static final String LOCAL_DEFAULT_URL = "jdbc:postgresql://localhost:5432/trademesh";

    /**
     * Flyway is disabled through command-line arguments rather than
     * {@code SpringApplicationBuilder.properties(..)}: the latter registers
     * default properties, which sit at the BOTTOM of the precedence order and
     * lose to application.yml's own {@code spring.flyway.enabled: true}.
     */
    private static final String NO_FLYWAY = "--spring.flyway.enabled=false";

    private static final String NO_DATASOURCE =
        "--spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration";

    private static final String TEST_JWT_SECRET =
        "--trademesh.security.jwt.secret=test-only-auth-secret-32-characters";

    @Test
    void aNonLocalProfileRefusesToStartWithoutExplicitDatabaseCredentials() {
        assumeTrue(System.getenv("DATABASE_URL") == null,
            "DATABASE_URL is set in this environment, which would supply the value under test");

        assertThatThrownBy(() -> run("production", NO_FLYWAY).close())
            .as("a non-local profile must fail rather than start")
            .isInstanceOf(Exception.class)
            .hasStackTraceContaining("must start with \"jdbc\"");
    }

    /**
     * The failure above must be an UNRESOLVED placeholder, not a connection
     * attempt. If the local default ever leaks into the base document the
     * application would start and quietly point at localhost with a published
     * password — which is the outcome the profile split exists to prevent.
     */
    @Test
    void aNonLocalProfileNeverInheritsTheLocalDefaultUrl() {
        assumeTrue(System.getenv("DATABASE_URL") == null,
            "DATABASE_URL is set in this environment, which would supply the value under test");

        Throwable failure = catchThrowable(() -> run("production", NO_FLYWAY).close());

        assertThat(failure).isNotNull();
        assertThat(stackTraceOf(failure))
            .as("the local default must not reach a non-local profile")
            .doesNotContain(LOCAL_DEFAULT_URL);
    }

    private static String stackTraceOf(Throwable failure) {
        StringWriter trace = new StringWriter();
        failure.printStackTrace(new PrintWriter(trace));
        return trace.toString();
    }

    @Test
    void theLocalProfileSuppliesItsOwnDatasourceDefaults() {
        try (ConfigurableApplicationContext context = run(
            "local", NO_FLYWAY, NO_DATASOURCE, "--spring.main.lazy-initialization=true")) {
            assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                .isEqualTo(LOCAL_DEFAULT_URL);
            assertThat(context.getEnvironment().getProperty("spring.datasource.username"))
                .isEqualTo("trademesh");
        }
    }

    private static ConfigurableApplicationContext run(String profile, String... args) {
        String[] applicationArgs = new String[args.length + 1];
        applicationArgs[0] = TEST_JWT_SECRET;
        System.arraycopy(args, 0, applicationArgs, 1, args.length);
        return new SpringApplicationBuilder(TradeMeshApplication.class)
            .web(WebApplicationType.NONE)
            .profiles(profile)
            .run(applicationArgs);
    }
}
