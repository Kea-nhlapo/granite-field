package za.co.trademesh.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import za.co.trademesh.modules.access.application.PhoneIdentityRepository;

class MessagingProviderBoundaryTest {

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("za.co.trademesh");

    @Test
    void twilioTypesStayInsideTheirOptionalAdapters() {
        noClasses()
                .that()
                .resideOutsideOfPackages("..modules.notification.infrastructure..", "..modules.access.infrastructure..")
                .should()
                .dependOnClassesThat()
                .haveSimpleNameStartingWith("Twilio")
                .because("business workflows must remain independent of the selected messaging provider")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void storedPhoneVerificationMethodsDoNotNameAnOtpProvider() {
        assertThat(PhoneIdentityRepository.VerificationMethod.values())
                .extracting(Enum::name)
                .containsExactlyInAnyOrder("OTP", "MOMO_CONSENT");
    }
}
