package za.co.trademesh.modules.access.infrastructure;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.access.domain.UserAccount;
import za.co.trademesh.modules.access.domain.UserAccountRepository;
import za.co.trademesh.shared.security.AccountRole;

/** Creates opt-in accounts for demonstrations without putting a password in source control. */
@Component
@ConditionalOnProperty(prefix = "trademesh.demo-accounts", name = "enabled", havingValue = "true")
class DemoAccountSeeder implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoAccountSeeder.class);
    private static final int MINIMUM_PASSWORD_BYTES = 12;
    private static final int MAXIMUM_BCRYPT_PASSWORD_BYTES = 72;
    private static final List<DemoAccount> DEMO_ACCOUNTS = List.of(
            new DemoAccount("owner@demo.trademesh.test", AccountRole.BUSINESS_OWNER),
            new DemoAccount("supplier@demo.trademesh.test", AccountRole.SUPPLIER),
            new DemoAccount("transporter@demo.trademesh.test", AccountRole.TRANSPORTER),
            new DemoAccount("driver@demo.trademesh.test", AccountRole.DRIVER));

    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String password;

    DemoAccountSeeder(
            UserAccountRepository accounts,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${trademesh.demo-accounts.password:}") String password) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments ignored) {
        validatePassword();
        List<DemoAccount> missing = DEMO_ACCOUNTS.stream()
                .filter(account -> !accounts.emailExists(account.email()))
                .toList();
        if (missing.isEmpty()) {
            LOGGER.info("Demo accounts already exist; no accounts were created");
            return;
        }

        String passwordHash = passwordEncoder.encode(password);
        Instant now = clock.instant();
        for (DemoAccount account : missing) {
            UUID id = UUID.nameUUIDFromBytes(("trademesh-demo-account:" + account.email()).getBytes(UTF_8));
            accounts.save(new UserAccount(id, account.email(), passwordHash, true, now, Set.of(account.role())));
        }
        LOGGER.info("Seeded {} demo account(s)", missing.size());
    }

    private void validatePassword() {
        int passwordBytes = password.getBytes(UTF_8).length;
        if (passwordBytes < MINIMUM_PASSWORD_BYTES || passwordBytes > MAXIMUM_BCRYPT_PASSWORD_BYTES) {
            throw new IllegalStateException(
                    "DEMO_ACCOUNT_PASSWORD must contain between 12 and 72 UTF-8 bytes when demo accounts are enabled");
        }
    }

    private record DemoAccount(String email, AccountRole role) {}
}
