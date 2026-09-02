package za.co.trademesh.modules.business.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.business.domain.BusinessRepository;
import za.co.trademesh.modules.business.domain.BusinessVerificationStatus;

@Service
class BusinessTrustProfileService implements BusinessTrustCatalog {

    private final BusinessRepository businesses;

    BusinessTrustProfileService(BusinessRepository businesses) {
        this.businesses = businesses;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BusinessTrustFacts> find(UUID businessId) {
        return businesses
                .findBusinessById(businessId)
                .map(business -> new BusinessTrustFacts(
                        business.id(),
                        business.verificationStatus() == BusinessVerificationStatus.REGISTRY_VERIFIED,
                        false));
    }
}
