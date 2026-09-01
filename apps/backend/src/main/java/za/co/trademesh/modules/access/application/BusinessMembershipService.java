package za.co.trademesh.modules.access.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import za.co.trademesh.modules.access.domain.BusinessMembershipRepository;

@Service
public class BusinessMembershipService {

    private final BusinessMembershipRepository memberships;
    private final Clock clock;

    public BusinessMembershipService(BusinessMembershipRepository memberships, Clock clock) {
        this.memberships = memberships;
        this.clock = clock;
    }

    public void grantOwner(UUID businessId, UUID userId) {
        memberships.grantOwner(businessId, userId, clock.instant());
    }
}
