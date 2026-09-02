package za.co.trademesh.modules.business.infrastructure;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.business.application.CompanyRegistryProvider;
import za.co.trademesh.modules.business.domain.RegistrationNumber;

/**
 * Default company registry provider. Fails loudly so an environment that has not chosen a provider
 * cannot quietly fall back to mocked registry data.
 */
@Component
@ConditionalOnProperty(
        prefix = "trademesh.business.registry",
        name = "provider",
        havingValue = "unconfigured",
        matchIfMissing = true)
class UnconfiguredCompanyRegistryProvider implements CompanyRegistryProvider {

    @Override
    public Optional<RegistryCompany> findByRegistrationNumber(RegistrationNumber registrationNumber) {
        throw new IllegalStateException(
                "No company registry provider is configured; set trademesh.business.registry.provider");
    }
}
