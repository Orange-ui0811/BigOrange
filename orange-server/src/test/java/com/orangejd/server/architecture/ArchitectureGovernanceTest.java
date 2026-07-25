package com.orangejd.server.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureGovernanceTest {
    private final JavaClasses classes = new ClassFileImporter()
            .importPackages("com.orangejd.server");

    @Test
    void foundationMustNotDependOnBusinessOrWebLayers() {
        noClasses()
                .that().resideInAPackage("..foundation..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..controller..", "..contribution..", "..service..")
                .check(classes);
    }

    @Test
    void governedPackagesUseConstructorInjection() {
        fields()
                .that().areDeclaredInClassesThat()
                .resideInAnyPackage("..foundation..", "..contribution..", "..security..")
                .should().notBeAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class)
                .check(classes);
    }
}
