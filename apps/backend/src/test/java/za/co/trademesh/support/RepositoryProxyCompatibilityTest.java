package za.co.trademesh.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RepositoryProxyCompatibilityTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "za.co.trademesh.modules.access.infrastructure.JdbcUserAccountRepository",
                "za.co.trademesh.modules.access.infrastructure.JdbcRefreshSessionRepository",
                "za.co.trademesh.shared.security.JdbcTenantMembershipLookup"
            })
    void repositoryBeansCanBeSubclassedBySpring(String className) throws ClassNotFoundException {
        Class<?> repositoryType = Class.forName(className);

        assertThat(Modifier.isFinal(repositoryType.getModifiers()))
                .as("Spring class-based proxies cannot subclass %s", className)
                .isFalse();
    }
}
