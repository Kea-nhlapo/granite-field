package za.co.trademesh.modules.access.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.access.domain.UserAccountRepository;

/** Public access-module contract for proving which account owns an authenticated subject. */
@Service
public class AccountIdentityService {

    private final UserAccountRepository accounts;

    public AccountIdentityService(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public Optional<AccountIdentity> findEnabled(UUID userId) {
        return accounts.findById(userId)
                .filter(account -> account.enabled())
                .map(account -> new AccountIdentity(account.id(), Optional.ofNullable(account.email())));
    }

    public record AccountIdentity(UUID userId, Optional<String> normalizedEmail) {}
}
