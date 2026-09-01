package za.co.trademesh.modules.business.application;

import java.util.Objects;
import java.util.Optional;
import za.co.trademesh.modules.business.domain.RegistrationNumber;

public interface CompanyRegistryProvider {

    Optional<RegistryCompany> findByRegistrationNumber(RegistrationNumber registrationNumber);

    record RegistryCompany(String legalName, String tradingName, String registeredAddress, String registryReference) {
        public RegistryCompany {
            Objects.requireNonNull(legalName, "legalName");
            Objects.requireNonNull(registeredAddress, "registeredAddress");
            Objects.requireNonNull(registryReference, "registryReference");
        }
    }
}
