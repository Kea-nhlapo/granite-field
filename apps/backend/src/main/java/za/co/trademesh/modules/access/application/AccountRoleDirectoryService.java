package za.co.trademesh.modules.access.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.access.domain.UserAccountRepository;
import za.co.trademesh.shared.security.AccountRole;

@Service
class AccountRoleDirectoryService implements AccountRoleDirectory {

    private final UserAccountRepository accounts;

    AccountRoleDirectoryService(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActiveWithRole(UUID userId, AccountRole role) {
        return userId != null
                && role != null
                && accounts.findById(userId)
                        .filter(account -> account.enabled() && account.roles().contains(role))
                        .isPresent();
    }
}
