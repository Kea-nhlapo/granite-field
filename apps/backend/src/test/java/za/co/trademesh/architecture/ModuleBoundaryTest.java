package za.co.trademesh.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModuleBoundaryTest {

    private static final String ROOT_PACKAGE = "za.co.trademesh";
    private static final String MODULES_PACKAGE = ROOT_PACKAGE + ".modules";
    private static final Set<String> PRIVATE_LAYERS = Set.of("domain", "infrastructure", "internal");

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT_PACKAGE);

    @Test
    void modulesDoNotReachIntoAnotherModulesPrivateLayers() {
        ArchRule rule = classes()
                .that()
                .resideInAPackage(MODULES_PACKAGE + "..")
                .should(new StayOutOfOtherModulesPrivateLayers())
                .allowEmptyShould(true)
                .because(
                        "modules may collaborate through public API or application contracts, not another module's internals");

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void domainCodeDoesNotDependOnWebDatabaseOrExternalAdapters() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework.web..",
                        "org.springframework.jdbc..",
                        "org.springframework.data..",
                        "org.springframework.orm..",
                        "jakarta.persistence..",
                        "..infrastructure..",
                        "..adapter..",
                        "..adapters..",
                        "..client..",
                        "..clients..",
                        "..external..")
                .allowEmptyShould(true)
                .because("domain rules must remain independent of delivery, persistence, and external systems");

        rule.check(PRODUCTION_CLASSES);
    }

    private static final class StayOutOfOtherModulesPrivateLayers extends ArchCondition<JavaClass> {

        private StayOutOfOtherModulesPrivateLayers() {
            super("stay out of another module's domain, infrastructure, and internal packages");
        }

        @Override
        public void check(JavaClass source, ConditionEvents events) {
            Optional<String> sourceModule = moduleName(source);
            if (sourceModule.isEmpty()) {
                return;
            }

            for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                Optional<String> targetModule = moduleName(target);

                if (targetModule.isPresent()
                        && !sourceModule.get().equals(targetModule.get())
                        && isPrivateLayer(target, targetModule.get())) {
                    events.add(SimpleConditionEvent.violated(source, dependency.getDescription()));
                }
            }
        }

        private static boolean isPrivateLayer(JavaClass target, String module) {
            String moduleRoot = MODULES_PACKAGE + "." + module;
            String packageName = target.getPackageName();
            if (!packageName.startsWith(moduleRoot + ".")) {
                return false;
            }

            String relativePackage = packageName.substring(moduleRoot.length() + 1);
            String firstLayer = relativePackage.split("\\.", 2)[0];
            return PRIVATE_LAYERS.contains(firstLayer);
        }

        private static Optional<String> moduleName(JavaClass type) {
            String packageName = type.getPackageName();
            if (!packageName.startsWith(MODULES_PACKAGE + ".")) {
                return Optional.empty();
            }

            String remainder = packageName.substring(MODULES_PACKAGE.length() + 1);
            int separator = remainder.indexOf('.');
            String module = separator < 0 ? remainder : remainder.substring(0, separator);
            return module.isBlank() ? Optional.empty() : Optional.of(module);
        }
    }
}
