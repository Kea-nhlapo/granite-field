package za.co.trademesh.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

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
}
