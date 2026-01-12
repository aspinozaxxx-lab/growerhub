package ru.growerhub.backend.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureRulesTest {
    // spisok domenov dlya facade-pravila
    private static final String[] DOMAINS = {
            "auth",
            "device",
            "firmware",
            "journal",
            "plant",
            "pump",
            "sensor",
            "user"
    };

    // klassy vneshney arhitektury dlya proverki
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("ru.growerhub.backend");

    // pravilo: na kazhdyj domen dolzhen byt odin public facade
    @Test
    void eachDomainHasSingleFacade() {
        for (String domain : DOMAINS) {
            List<JavaClass> facades = CLASSES.stream()
                    .filter(it -> it.getPackageName().startsWith("ru.growerhub.backend." + domain))
                    .filter(it -> it.getSimpleName().endsWith("Facade"))
                    .filter(it -> it.getModifiers().contains(JavaModifier.PUBLIC))
                    .collect(Collectors.toList());
            // sosednee pravilo: dlja nahozhdeniya facade-dlya domena
            Assertions.assertEquals(1, facades.size(), "Expected exactly one public Facade in " + domain);
        }
    }

    // pravilo: internal-dostup ogranichen v ramkakh domena
    @Test
    void internalModulesOnlyUsedByOwnDomain() {
        for (String domain : DOMAINS) {
            ArchRule isolationRule = classes()
                    .that().resideInAPackage("ru.growerhub.backend." + domain + ".internal..")
                    .should().onlyBeAccessed().byClassesThat().resideInAPackage("ru.growerhub.backend." + domain + "..");
            isolationRule.check(CLASSES);
        }
    }

    // pravilo: common.contract i common.util bez anotacii Spring i Entity
    @Test
    void commonContractAndUtilStayClean() {
        // net komponentov v common.contract
        noAnnotationRule("ru.growerhub.backend.common.contract..", Component.class).check(CLASSES);
        // net servisov v common.contract
        noAnnotationRule("ru.growerhub.backend.common.contract..", Service.class).check(CLASSES);
        // net repository v common.contract
        noAnnotationRule("ru.growerhub.backend.common.contract..", Repository.class).check(CLASSES);
        // net konfiguracij v common.contract
        noAnnotationRule("ru.growerhub.backend.common.contract..", Configuration.class).check(CLASSES);
        // net entity v common.contract
        noAnnotationRule("ru.growerhub.backend.common.contract..", Entity.class).check(CLASSES);

        // net komponentov v common.util
        noAnnotationRule("ru.growerhub.backend.common.util..", Component.class).check(CLASSES);
        // net servisov v common.util
        noAnnotationRule("ru.growerhub.backend.common.util..", Service.class).check(CLASSES);
        // net repository v common.util
        noAnnotationRule("ru.growerhub.backend.common.util..", Repository.class).check(CLASSES);
        // net konfiguracij v common.util
        noAnnotationRule("ru.growerhub.backend.common.util..", Configuration.class).check(CLASSES);
        // net entity v common.util
        noAnnotationRule("ru.growerhub.backend.common.util..", Entity.class).check(CLASSES);
    }

    // pravilo: internal ne mozhet zaviset ot common.component
    @Test
    void internalShouldNotDependOnCommonComponent() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("ru.growerhub.backend..internal..")
                .should().dependOnClassesThat().resideInAPackage("ru.growerhub.backend.common.component..");
        rule.check(CLASSES);
    }

    // helper dlja pravila bez anotacij
    private static ArchRule noAnnotationRule(String packagePattern, Class<? extends Annotation> annotation) {
        return noClasses()
                .that().resideInAPackage(packagePattern)
                .should().beAnnotatedWith(annotation)
                .allowEmptyShould(true);
    }
}
