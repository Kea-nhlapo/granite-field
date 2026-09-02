package za.co.trademesh.modules.access.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.access.domain.UserAccount;
import za.co.trademesh.modules.access.domain.UserAccountRepository;
import za.co.trademesh.shared.security.AccountRole;

@Service
class ExternalAccountService {

    private final PhoneIdentityRepository phoneIdentities;
    private final UserAccountRepository accounts;
    private final Clock clock;

    ExternalAccountService(PhoneIdentityRepository phoneIdentities, UserAccountRepository accounts, Clock clock) {
        this.phoneIdentities = phoneIdentities;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional
    public UUID resolve(String phoneNumber, PhoneIdentityRepository.VerificationMethod method) {
        return phoneIdentities.findUserId(phoneNumber).orElseGet(() -> create(phoneNumber, method));
    }

    private UUID create(String phoneNumber, PhoneIdentityRepository.VerificationMethod method) {
        Instant now = clock.instant();
        UUID userId = UUID.randomUUID();
        accounts.save(new UserAccount(userId, null, null, true, now, Set.of(AccountRole.BUSINESS_OWNER)));
        phoneIdentities.save(phoneNumber, userId, method, now);
        return userId;
    }
}
