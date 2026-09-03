package za.co.trademesh.modules.access.application;

import java.time.Clock;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.access.domain.UserAccount;
import za.co.trademesh.modules.access.domain.UserAccountRepository;
import za.co.trademesh.modules.notification.application.NotificationContactService;
import za.co.trademesh.modules.notification.application.NotificationPreferenceService;
import za.co.trademesh.modules.payment.application.SandboxWalletService;
import za.co.trademesh.shared.security.AccountRole;

@Component
@Profile("local")
@ConditionalOnProperty(prefix = "trademesh.sandbox-wallet", name = "enabled", havingValue = "true")
class LocalDemoAccountSeeder implements ApplicationRunner {

    private static final String LUNGILE_PASSWORD_HASH =
            "{bcrypt}$2b$12$sWfcbMP.fu3EzG/WmbnLzeFaMhaa1wNd9pfCEt7cPrbyQoEt/zf4W";
    private static final String LUNGILE_PHONE = "+27846134525";

    private final UserAccountRepository accounts;
    private final JdbcTemplate jdbcTemplate;
    private final SandboxWalletService wallets;
    private final NotificationContactService contacts;
    private final NotificationPreferenceService preferences;
    private final Clock clock;

    LocalDemoAccountSeeder(
            UserAccountRepository accounts,
            JdbcTemplate jdbcTemplate,
            SandboxWalletService wallets,
            NotificationContactService contacts,
            NotificationPreferenceService preferences,
            Clock clock) {
        this.accounts = accounts;
        this.jdbcTemplate = jdbcTemplate;
        this.wallets = wallets;
        this.contacts = contacts;
        this.preferences = preferences;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (accounts.findByEmail(SandboxWalletService.LUNGILE_EMAIL).isEmpty()) {
            accounts.save(new UserAccount(
                    SandboxWalletService.LUNGILE_USER_ID,
                    SandboxWalletService.LUNGILE_EMAIL,
                    LUNGILE_PASSWORD_HASH,
                    true,
                    clock.instant(),
                    Set.of(AccountRole.SUPPLIER)));
        }

        jdbcTemplate.update(
                """
            INSERT INTO supplier_profile (
                id, normalized_email, profile_status, claimed_user_id, business_id, created_at, converted_at
            ) VALUES (?, ?, 'REGISTERED', ?, NULL, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """,
                SandboxWalletService.LUNGILE_SUPPLIER_PROFILE_ID,
                SandboxWalletService.LUNGILE_EMAIL,
                SandboxWalletService.LUNGILE_USER_ID,
                java.time.OffsetDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC),
                java.time.OffsetDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC));

        wallets.initializeLungile();
        contacts.save(SandboxWalletService.LUNGILE_USER_ID, LUNGILE_PHONE, true, false);
        preferences.enableShipmentSms(SandboxWalletService.LUNGILE_USER_ID);
    }
}
