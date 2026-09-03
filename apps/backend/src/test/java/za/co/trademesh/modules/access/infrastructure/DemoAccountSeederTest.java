package za.co.trademesh.modules.access.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import za.co.trademesh.modules.access.domain.UserAccount;
import za.co.trademesh.modules.access.domain.UserAccountRepository;
import za.co.trademesh.shared.security.AccountRole;

@ExtendWith(MockitoExtension.class)
class DemoAccountSeederTest {

    private static final String DEMO_PASSWORD = "TradeMeshDemo!2026";
    private static final Instant NOW = Instant.parse("2026-09-03T08:00:00Z");

    @Mock
    private UserAccountRepository accounts;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void createsEveryMissingRoleAndSkipsAnExistingAccount() {
        when(accounts.emailExists(anyString()))
                .thenAnswer(
                        invocation -> invocation.getArgument(0, String.class).equals("supplier@demo.trademesh.test"));
        when(passwordEncoder.encode(DEMO_PASSWORD)).thenReturn("{bcrypt}demo-hash");

        new DemoAccountSeeder(accounts, passwordEncoder, Clock.fixed(NOW, ZoneOffset.UTC), DEMO_PASSWORD).run(null);

        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(accounts, times(3)).save(saved.capture());
        verify(passwordEncoder).encode(DEMO_PASSWORD);

        assertThat(saved.getAllValues())
                .extracting(UserAccount::email)
                .containsExactly(
                        "owner@demo.trademesh.test",
                        "transporter@demo.trademesh.test",
                        "driver@demo.trademesh.test");
        assertThat(saved.getAllValues()).allSatisfy(account -> {
            assertThat(account.passwordHash()).isEqualTo("{bcrypt}demo-hash");
            assertThat(account.enabled()).isTrue();
            assertThat(account.createdAt()).isEqualTo(NOW);
        });
        assertThat(saved.getAllValues())
                .flatExtracting(UserAccount::roles)
                .containsExactly(
                        AccountRole.BUSINESS_OWNER,
                        AccountRole.TRANSPORTER,
                        AccountRole.DRIVER);
    }

    @Test
    void refusesToStartWithAnUnsafeDemoPassword() {
        DemoAccountSeeder seeder =
                new DemoAccountSeeder(accounts, passwordEncoder, Clock.fixed(NOW, ZoneOffset.UTC), "too-short");

        assertThatThrownBy(() -> seeder.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEMO_ACCOUNT_PASSWORD");
    }
}
