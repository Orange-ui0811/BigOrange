package com.orangejd.server.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureGovernanceTest {
    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
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
                .that().areDeclaredInClassesThat().resideInAPackage("com.orangejd.server..")
                .should().notBeAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class)
                .check(classes);
    }

    @Test
    void fieldInjectionDebtMustOnlyDecrease() {
        Set<String> actual = classes.stream()
                .filter(javaClass -> javaClass.getFields().stream()
                        .anyMatch(field -> field.isAnnotatedWith(Autowired.class)))
                .map(JavaClass::getName)
                .collect(Collectors.toCollection(HashSet::new));

        assertTrue(actual.isEmpty(), () -> "Field injection must be zero: " + actual);
    }

    @Test
    void controllersMustNotAddDirectMapperDependencies() {
        Set<String> actual = classes.stream()
                .filter(javaClass -> javaClass.getPackageName().contains(".controller"))
                .filter(javaClass -> javaClass.getDirectDependenciesFromSelf().stream()
                        .anyMatch(dependency -> dependency.getTargetClass().getPackageName().contains(".mapper")))
                .map(JavaClass::getName)
                .collect(Collectors.toCollection(HashSet::new));

        assertTrue(actual.isEmpty(), () -> "Controller to Mapper dependencies must be zero: " + actual);
    }

    @Test
    void stableLayerDirectionsRemainEnforced() {
        noClasses()
                .that().resideInAPackage("..service..")
                .should().dependOnClassesThat().resideInAPackage("..controller..")
                .check(classes);
        noClasses()
                .that().resideInAPackage("..task..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..controller..", "..mapper..")
                .check(classes);
        noClasses()
                .that().resideInAPackage("..mapper..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..controller..", "..service..")
                .check(classes);
    }

    private void assertNoNewDebt(String section, Set<String> actual) {
        Set<String> baseline = baseline().getOrDefault(section, Set.of());
        Set<String> unexpected = new HashSet<>(actual);
        unexpected.removeAll(baseline);
        assertTrue(unexpected.isEmpty(), () -> "New " + section + " architecture debt: " + unexpected);
    }

    private Map<String, Set<String>> baseline() {
        InputStream stream = getClass().getResourceAsStream("/architecture-baseline.txt");
        if (stream == null) throw new IllegalStateException("Missing architecture-baseline.txt");
        Map<String, Set<String>> sections = new HashMap<>();
        String current = "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String raw : reader.lines().toList()) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("[") && line.endsWith("]")) {
                    current = line.substring(1, line.length() - 1);
                    sections.computeIfAbsent(current, ignored -> new HashSet<>());
                } else {
                    sections.computeIfAbsent(current, ignored -> new HashSet<>()).add(line);
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read architecture baseline", exception);
        }
        return sections;
    }
}
