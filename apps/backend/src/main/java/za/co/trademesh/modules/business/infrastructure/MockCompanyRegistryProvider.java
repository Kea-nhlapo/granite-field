package za.co.trademesh.modules.business.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.business.application.CompanyRegistryProvider;
import za.co.trademesh.modules.business.domain.RegistrationNumber;

@Component
@Profile("local")
class MockCompanyRegistryProvider implements CompanyRegistryProvider {

    private static final List<String> DEMO_ADDRESSES = List.of(
            "42 Madiba Street, Tembisa, Gauteng",
            "18 Vilakazi Road, Soweto, Gauteng",
            "7 Church Street, Polokwane, Limpopo",
            "25 West Street, Durban, KwaZulu-Natal");

    @Override
    public Optional<RegistryCompany> findByRegistrationNumber(RegistrationNumber registrationNumber) {
        String digits = registrationNumber.digits();
        if (registrationNumber.value().equals("2024/123456/07")) {
            return Optional.of(new RegistryCompany(
                    "Mahlako General Trading (Pty) Ltd",
                    "Mahlako General Store",
                    DEMO_ADDRESSES.getFirst(),
                    "MOCK-CIPC-" + digits));
        }

        int addressIndex = Math.floorMod(digits.hashCode(), DEMO_ADDRESSES.size());
        String shortNumber = digits.substring(4, 10);
        return Optional.of(new RegistryCompany(
                "Demo Business " + shortNumber + " (Pty) Ltd",
                "Demo Business " + shortNumber,
                DEMO_ADDRESSES.get(addressIndex),
                "MOCK-CIPC-" + digits));
    }
}
