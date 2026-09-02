package za.co.trademesh.modules.access.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.access.domain.UserAccountRepository;

@Service
class ActiveUserDirectoryService implements ActiveUserDirectory {

    private final UserAccountRepository accounts;

    ActiveUserDirectoryService(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(UUID userId) {
        return userId != null
                && accounts.findById(userId)
                        .filter(account -> account.enabled())
                        .isPresent();
    }
}
